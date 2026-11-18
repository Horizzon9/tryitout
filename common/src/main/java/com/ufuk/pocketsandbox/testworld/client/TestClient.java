package com.ufuk.pocketsandbox.testworld.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ufuk.pocketsandbox.PocketSandbox;
import com.ufuk.pocketsandbox.screen.SandboxScreen;
import com.ufuk.pocketsandbox.testworld.SceneSnapshot;
import com.ufuk.pocketsandbox.testworld.TestWorld;
import com.ufuk.pocketsandbox.testworld.net.TestActionPayload;
import com.ufuk.pocketsandbox.testworld.net.TestEnterPayload;
import com.ufuk.pocketsandbox.testworld.net.TestLeftPayload;
import com.ufuk.pocketsandbox.testworld.net.TestSettingsPayload;
import com.ufuk.pocketsandbox.testworld.net.TestStatusPayload;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import java.io.IOException;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of the test world: availability, requests, the server's status messages and the pocket scene
 * that reopens with the room as it was left.
 */
public final class TestClient {
    /** Leaves the test world (default Y); does nothing outside it. */
    public static final KeyMapping EXIT_KEY = new KeyMapping("key.pocketsandbox.test_exit",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.pocketsandbox");
    /**
     * Opens the test world from anywhere in the world (no key by default): outside it, the test world list with a
     * fresh room holding the block in hand; inside it, the test world settings.
     */
    public static final KeyMapping OPEN_TEST_KEY = new KeyMapping("key.pocketsandbox.test_open",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.pocketsandbox");
    /** Saves the test world as it is right now (no key by default; set one in Controls). */
    public static final KeyMapping QUICK_SAVE_KEY = new KeyMapping("key.pocketsandbox.test_quick_save",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.pocketsandbox");

    private static boolean inTest;
    /** The item whose scene entered the test world: the reopened scene is built around it. */
    @Nullable
    private static ItemStack lastTarget;
    /** Room received on leaving, opened once the player is back in the real world. */
    @Nullable
    private static byte[] pendingRoom;
    /** Saved test worlds as the server last reported them. */
    private static com.ufuk.pocketsandbox.testworld.net.TestSavesPayload saves =
            new com.ufuk.pocketsandbox.testworld.net.TestSavesPayload(java.util.List.of(), 0, "", false);
    /** Shows the light level of the looked-at spot on the action bar (test world only, client side). */
    private static boolean showLight;
    /** Last settings the server reported for the room we are in. */
    private static TestSettingsPayload settings = new TestSettingsPayload(0, 0, 0, 0, 1);

    private TestClient() {
    }

    public static void init() {
        KeyMappingRegistry.register(EXIT_KEY);
        KeyMappingRegistry.register(QUICK_SAVE_KEY);
        KeyMappingRegistry.register(OPEN_TEST_KEY);
        dev.architectury.event.events.client.ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> {
            if (inTest) {
                DummyReachRing.renderHud(graphics);
                TestCenterMarker.renderHud(graphics);
            }
        });
        ClientTickEvent.CLIENT_POST.register(TestClient::tick);
        NetworkManager.registerReceiver(NetworkManager.s2c(), com.ufuk.pocketsandbox.testworld.net.TestAreaPayload.TYPE, com.ufuk.pocketsandbox.testworld.net.TestAreaPayload.CODEC,
                (payload, context) -> context.queue(() -> com.ufuk.pocketsandbox.testworld.TestBorderShapes.setClientBox(
                        new double[] {payload.minX(), payload.minY(), payload.minZ(), payload.maxX(), payload.maxY(),
                                payload.maxZ()})));
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            com.ufuk.pocketsandbox.testworld.TestBorderShapes.setClientBox(null);
            inTest = false;
            pendingRoom = null;
        });
        NetworkManager.registerReceiver(NetworkManager.s2c(), TestStatusPayload.TYPE, TestStatusPayload.CODEC,
                (payload, context) -> context.queue(() -> onStatus(payload)));
        NetworkManager.registerReceiver(NetworkManager.s2c(), com.ufuk.pocketsandbox.testworld.net.TestSavesPayload.TYPE,
                com.ufuk.pocketsandbox.testworld.net.TestSavesPayload.CODEC,
                (payload, context) -> context.queue(() -> saves = payload));
        NetworkManager.registerReceiver(NetworkManager.s2c(),
                com.ufuk.pocketsandbox.testworld.net.TestMaterialsPayload.TYPE,
                com.ufuk.pocketsandbox.testworld.net.TestMaterialsPayload.CODEC,
                (payload, context) -> context.queue(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.setScreen(new com.ufuk.pocketsandbox.screen.TestMaterialsScreen(mc.screen, payload));
                }));
        NetworkManager.registerReceiver(NetworkManager.s2c(),
                com.ufuk.pocketsandbox.testworld.net.TestSettingsSyncPayload.TYPE,
                com.ufuk.pocketsandbox.testworld.net.TestSettingsSyncPayload.CODEC,
                (payload, context) -> context.queue(() -> settings = payload.settings()));
        // "Test settings" button on the inventory (survival and creative) while inside the test world.
        dev.architectury.event.events.client.ClientGuiEvent.INIT_POST.register((screen, access) -> {
            if (inTest && (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                    || screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)) {
                var gui = (com.ufuk.pocketsandbox.mixin.AbstractContainerScreenAccessor) screen;
                int x = gui.pocketsandbox$getLeftPos() - 84;
                int y = gui.pocketsandbox$getTopPos();
                if (x < 2) {
                    x = 2;
                    y = 2;
                }
                access.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.translatable("pocketsandbox.button.test_settings"),
                        b -> Minecraft.getInstance().setScreen(new com.ufuk.pocketsandbox.screen.TestSettingsScreen(screen)))
                        .bounds(x, y, 80, 20).build());
            }
        });
        NetworkManager.registerReceiver(NetworkManager.s2c(), TestLeftPayload.TYPE, TestLeftPayload.CODEC,
                (payload, context) -> context.queue(() -> pendingRoom = payload.data()));
    }

    /** True only on a server (or single player world) that has Pocket Sandbox too. */
    public static boolean available() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null && mc.level != null && NetworkManager.canServerReceive(TestActionPayload.TYPE);
    }

    public static com.ufuk.pocketsandbox.testworld.net.TestSavesPayload saves() {
        return saves;
    }

    public static boolean showLight() {
        return showLight;
    }

    public static void toggleLight() {
        showLight = !showLight;
    }

    /**
     * Leaving the test world: with "ask on exit" on (default) and saving possible, the save / leave / cancel
     * window opens; otherwise the player leaves at once, unsaved.
     */
    private static void openTestWorld(Minecraft mc) {
        if (inTest) {
            mc.setScreen(new com.ufuk.pocketsandbox.screen.TestSettingsScreen(null));
            return;
        }
        if (!available()) {
            message("pocketsandbox.test.not_available");
            return;
        }
        ItemStack held = mc.player.getMainHandItem().copyWithCount(1);
        java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> blocks =
                new java.util.HashMap<>();
        if (held.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
            blocks.put(new net.minecraft.core.BlockPos(0, 1, 0), blockItem.getBlock().defaultBlockState());
        }
        SceneSnapshot room = new SceneSnapshot(com.ufuk.pocketsandbox.config.SandboxConfig.get().defaultFloorSize,
                com.ufuk.pocketsandbox.scene.FloorType.PONDER,
                java.util.EnumSet.noneOf(com.ufuk.pocketsandbox.scene.WallSide.class), false, true, blocks);
        mc.setScreen(new com.ufuk.pocketsandbox.screen.TestWorldsScreen(null, held, room));
    }

    /** Saves under the name this world was saved / loaded as (the server picks "Quick save" for a new one). */
    public static void quickSave() {
        if (inTest && saves.canSave()) {
            request(TestActionPayload.Action.SAVE, "");
        } else if (inTest) {
            message("pocketsandbox.test_save.cannot");
        }
    }

    public static void requestExit(@Nullable net.minecraft.client.gui.screens.Screen parent) {
        if (!inTest) {
            return;
        }
        if (com.ufuk.pocketsandbox.config.SandboxConfig.get().askOnExit && saves.canSave()) {
            Minecraft.getInstance().setScreen(new com.ufuk.pocketsandbox.screen.TestSaveScreen(parent, true));
        } else {
            request(TestActionPayload.Action.EXIT, "");
        }
    }

    public static TestSettingsPayload settings() {
        return settings;
    }

    public static void sendSettings(TestSettingsPayload wanted) {
        if (inTest && available()) {
            NetworkManager.sendToServer(wanted.sanitized());
        }
    }

    public static boolean inTest() {
        return inTest;
    }

    public static void request(TestActionPayload.Action action, String arg) {
        if (available()) {
            NetworkManager.sendToServer(new TestActionPayload(action, arg));
        }
    }

    /** Enters the test world with the pocket scene's room. */
    public static void enter(ItemStack target, SceneSnapshot room) {
        if (!available()) {
            return;
        }
        byte[] data;
        try {
            data = room.toBytes();
        } catch (IOException e) {
            PocketSandbox.LOGGER.warn("Pocket Sandbox: could not pack the scene", e);
            return;
        }
        if (data.length > TestEnterPayload.MAX_BYTES) {
            message("pocketsandbox.test.scene_too_large");
            return;
        }
        lastTarget = target.copy();
        NetworkManager.sendToServer(new TestEnterPayload(data, target.copyWithCount(1)));
    }

    private static void tick(Minecraft mc) {
        if (inTest && mc.level != null && TestWorld.isTestWorld(mc.level)) {
            DummyReachRing.tick(mc);
            TestCenterMarker.tick(mc);
        }
        while (EXIT_KEY.consumeClick()) {
            if (inTest && mc.screen == null) {
                requestExit(null);
            }
        }
        while (OPEN_TEST_KEY.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                openTestWorld(mc);
            }
        }
        while (QUICK_SAVE_KEY.consumeClick()) {
            if (inTest && mc.screen == null) {
                quickSave();
            }
        }
        if (showLight && inTest && mc.player != null && mc.level != null && mc.player.tickCount % 5 == 0
                && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            net.minecraft.core.BlockPos spot = hit.getBlockPos().relative(hit.getDirection());
            int block = mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, spot);
            int sky = mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, spot);
            mc.player.displayClientMessage(Component.translatable("pocketsandbox.test.light", block, sky)
                    .withStyle(block == 0 ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.YELLOW), true);
        }
        // Back in the real world with no screen in the way: reopen the scene as it was left.
        if (pendingRoom != null && mc.screen == null && mc.player != null && mc.level != null
                && !TestWorld.isTestWorld(mc.level)) {
            byte[] data = pendingRoom;
            pendingRoom = null;
            if (lastTarget == null) {
                return;
            }
            try {
                SceneSnapshot room = SceneSnapshot.fromBytes(data, mc.level.holderLookup(Registries.BLOCK));
                mc.setScreen(new SandboxScreen(null, lastTarget, room));
            } catch (IOException | RuntimeException e) {
                PocketSandbox.LOGGER.warn("Pocket Sandbox: could not reopen the scene", e);
            }
        }
    }

    private static void onStatus(TestStatusPayload payload) {
        inTest = payload.inTest();
        if (!payload.messageKey().isEmpty()) {
            message(payload.messageKey());
        }
    }

    private static void message(String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(key), false);
        }
    }
}
