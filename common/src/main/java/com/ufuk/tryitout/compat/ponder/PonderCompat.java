package com.ufuk.tryitout.compat.ponder;

import com.ufuk.tryitout.TryItOutClient;
import com.ufuk.tryitout.scene.FloorType;
import com.ufuk.tryitout.testworld.SceneSnapshot;
import com.ufuk.tryitout.world.SandboxBounds;
import dev.architectury.platform.Platform;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Bridge to Ponder (Create's scene library, mod id {@code ponder}) without a compile dependency: everything goes
 * through reflection, bound once, and any mismatch (another Ponder version) only switches the bridge off.
 *
 * <p>Reads what the open Ponder screen shows right now: the blocks of every visible world section (with their
 * current offset) above the base plate, mapped to pocket scene coordinates (base plate centre at x/z = 0, base
 * plate layer = the floor). Block entity data and Ponder's entities are not copied.
 */
public final class PonderCompat {
    private static final String UI_CLASS = "net.createmod.ponder.foundation.ui.PonderUI";

    private static Boolean ready;
    private static Class<?> uiClass;
    private static Method sceneAccess;
    private static Method doScenesExist;
    private static Method activeScene;
    private static Method getWorld;
    private static Method forEachVisible;
    private static Method plateOffsetX;
    private static Method plateOffsetZ;
    private static Method plateSize;
    private static Method animatedOffset;
    private static Class<?> sectionElement;
    private static Field sectionField;
    private static Field stackField;
    private static final Map<Item, Boolean> HAS_SCENE = new IdentityHashMap<>();

    /** What the Ponder screen showed: the item it is about and the room for the scene / test world. */
    public record Capture(ItemStack stack, SceneSnapshot room) {
    }

    private PonderCompat() {
    }

    private static boolean ready() {
        if (ready == null) {
            ready = Platform.isModLoaded("ponder") && bind();
        }
        return ready;
    }

    private static boolean bind() {
        try {
            Class<?> index = Class.forName("net.createmod.ponder.foundation.PonderIndex");
            Class<?> access = Class.forName("net.createmod.ponder.api.registration.SceneRegistryAccess");
            Class<?> scene = Class.forName("net.createmod.ponder.foundation.PonderScene");
            Class<?> sectionImpl = Class.forName("net.createmod.ponder.foundation.element.WorldSectionElementImpl");
            uiClass = Class.forName(UI_CLASS);
            sceneAccess = index.getMethod("getSceneAccess");
            doScenesExist = access.getMethod("doScenesExistForId", net.minecraft.resources.ResourceLocation.class);
            activeScene = uiClass.getMethod("getActiveScene");
            getWorld = scene.getMethod("getWorld");
            sectionElement = Class.forName("net.createmod.ponder.api.element.WorldSectionElement");
            forEachVisible = scene.getMethod("forEachVisible", Class.class, Consumer.class);
            plateOffsetX = scene.getMethod("getBasePlateOffsetX");
            plateOffsetZ = scene.getMethod("getBasePlateOffsetZ");
            plateSize = scene.getMethod("getBasePlateSize");
            animatedOffset = sectionElement.getMethod("getAnimatedOffset");
            sectionField = sectionImpl.getDeclaredField("section");
            sectionField.setAccessible(true);
            stackField = uiClass.getDeclaredField("stack");
            stackField.setAccessible(true);
            TryItOutClient.LOGGER.info("TryItOut: Ponder support enabled");
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            TryItOutClient.LOGGER.warn("TryItOut: Ponder found but its classes did not match; Ponder"
                    + " support is off", e);
            return false;
        }
    }

    /** True for Ponder's scene screen. */
    public static boolean isPonderScreen(Screen screen) {
        return screen != null && ready() && uiClass.isInstance(screen);
    }

    /** True when Ponder has a scene for this item: Ponder's own W opens it, ours stays out of the way. */
    public static boolean hasScene(ItemStack stack) {
        if (stack.isEmpty() || !ready()) {
            return false;
        }
        return HAS_SCENE.computeIfAbsent(stack.getItem(), item -> {
            try {
                return (Boolean) doScenesExist.invoke(sceneAccess.invoke(null), BuiltInRegistries.ITEM.getKey(item));
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        });
    }

    /** The open Ponder screen's current scene as a pocket room; empty if nothing could be read. */
    public static Optional<Capture> capture(Screen screen) {
        if (!isPonderScreen(screen)) {
            return Optional.empty();
        }
        try {
            Object scene = activeScene.invoke(screen);
            if (scene == null) {
                return Optional.empty();
            }
            Level level = (Level) getWorld.invoke(scene);
            int size = (Integer) plateSize.invoke(scene);
            int centreX = (Integer) plateOffsetX.invoke(scene) + size / 2;
            int centreZ = (Integer) plateOffsetZ.invoke(scene) + size / 2;
            Map<BlockPos, BlockState> blocks = new HashMap<>();
            Consumer<Object> reader = element -> readSection(element, level, centreX, centreZ, blocks);
            forEachVisible.invoke(scene, sectionElement, reader);
            int reach = Math.max(1, size / 2);
            int top = 1;
            for (BlockPos pos : blocks.keySet()) {
                reach = Math.max(reach, Math.max(Math.abs(pos.getX()), Math.abs(pos.getZ())));
                top = Math.max(top, pos.getY());
            }
            int sceneSize = sceneSize(Math.max(reach * 2 + 1, top));
            ItemStack stack = (ItemStack) stackField.get(screen);
            if (stack == null || stack.isEmpty()) {
                stack = firstItem(blocks);
            }
            SceneSnapshot room = new SceneSnapshot(sceneSize, FloorType.PONDER, EnumSet.noneOf(
                    com.ufuk.tryitout.scene.WallSide.class), false, true, blocks);
            return Optional.of(new Capture(stack.copyWithCount(1), room));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            TryItOutClient.LOGGER.warn("TryItOut: could not read the Ponder scene", e);
            return Optional.empty();
        }
    }

    /** One visible world section: its blocks above the base plate, moved by its current offset. */
    private static void readSection(Object element, Level level, int centreX, int centreZ,
            Map<BlockPos, BlockState> blocks) {
        try {
            if (!sectionField.getDeclaringClass().isInstance(element)) {
                return;
            }
            Object section = sectionField.get(element);
            if (!(section instanceof Iterable<?> positions)) {
                return;
            }
            Vec3 offset = (Vec3) animatedOffset.invoke(element);
            int dx = offset == null ? 0 : (int) Math.round(offset.x);
            int dy = offset == null ? 0 : (int) Math.round(offset.y);
            int dz = offset == null ? 0 : (int) Math.round(offset.z);
            for (Object entry : positions) {
                if (!(entry instanceof BlockPos pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                int y = pos.getY() + dy;
                if (state.isAir() || y < 1) {
                    continue; // the base plate (y = 0) becomes our Ponder floor
                }
                blocks.put(new BlockPos(pos.getX() + dx - centreX, y, pos.getZ() + dz - centreZ), state);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            TryItOutClient.LOGGER.debug("TryItOut: skipped a Ponder section", e);
        }
    }

    /** Smallest pocket scene size that holds {@code needed} (the largest one when nothing does). */
    static int sceneSize(int needed) {
        for (int size : SandboxBounds.SIZES) {
            if (size >= needed) {
                return size;
            }
        }
        return SandboxBounds.SIZES[SandboxBounds.SIZES.length - 1];
    }

    private static ItemStack firstItem(Map<BlockPos, BlockState> blocks) {
        for (BlockState state : blocks.values()) {
            Item item = state.getBlock().asItem();
            if (item != Items.AIR) {
                return new ItemStack(item);
            }
        }
        return new ItemStack(Items.GRASS_BLOCK);
    }
}
