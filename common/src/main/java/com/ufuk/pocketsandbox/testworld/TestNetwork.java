package com.ufuk.pocketsandbox.testworld;

import com.ufuk.pocketsandbox.PocketSandbox;
import com.ufuk.pocketsandbox.testworld.net.TestActionPayload;
import com.ufuk.pocketsandbox.testworld.net.TestEnterPayload;
import com.ufuk.pocketsandbox.testworld.net.TestLeftPayload;
import com.ufuk.pocketsandbox.testworld.net.TestSettingsPayload;
import com.ufuk.pocketsandbox.testworld.net.TestStatusPayload;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.server.level.ServerPlayer;

/**
 * The test world channel. Registered on both sides; a client sees the test world only when the server answers
 * on this channel ({@code NetworkManager.canServerReceive}), so servers without the mod keep plain Pocket Sandbox.
 */
public final class TestNetwork {
    private TestNetwork() {
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.c2s(), TestActionPayload.TYPE, TestActionPayload.CODEC,
                (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        handle(player, payload);
                    }
                }));
        NetworkManager.registerReceiver(NetworkManager.c2s(), TestEnterPayload.TYPE, TestEnterPayload.CODEC,
                (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        enter(player, payload);
                    }
                }));
        NetworkManager.registerReceiver(NetworkManager.c2s(), TestSettingsPayload.TYPE, TestSettingsPayload.CODEC,
                (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        try {
                            TestSessions.applySettings(player, payload);
                        } catch (RuntimeException e) {
                            PocketSandbox.LOGGER.error("Pocket Sandbox: test world settings failed", e);
                        }
                    }
                }));
        TestEvents.init();
        if (Platform.getEnvironment() == Env.SERVER) {
            // On a client the S2C receivers are registered by the client side (TestClient).
            NetworkManager.registerS2CPayloadType(TestStatusPayload.TYPE, TestStatusPayload.CODEC);
            NetworkManager.registerS2CPayloadType(TestLeftPayload.TYPE, TestLeftPayload.CODEC);
            NetworkManager.registerS2CPayloadType(com.ufuk.pocketsandbox.testworld.net.TestSettingsSyncPayload.TYPE,
                    com.ufuk.pocketsandbox.testworld.net.TestSettingsSyncPayload.CODEC);
            NetworkManager.registerS2CPayloadType(com.ufuk.pocketsandbox.testworld.net.TestSavesPayload.TYPE,
                    com.ufuk.pocketsandbox.testworld.net.TestSavesPayload.CODEC);
            NetworkManager.registerS2CPayloadType(com.ufuk.pocketsandbox.testworld.net.TestAreaPayload.TYPE, com.ufuk.pocketsandbox.testworld.net.TestAreaPayload.CODEC);
            NetworkManager.registerS2CPayloadType(com.ufuk.pocketsandbox.testworld.net.TestMaterialsPayload.TYPE,
                    com.ufuk.pocketsandbox.testworld.net.TestMaterialsPayload.CODEC);
        }
    }

    private static void handle(ServerPlayer player, TestActionPayload payload) {
        try {
            switch (payload.action()) {
                case ENTER -> send(player, new TestStatusPayload(false, "pocketsandbox.test.use_scene"));
                case EXIT -> TestSessions.exit(player);
                case SAVE -> TestSessions.save(player, payload.arg());
                case LIST -> TestSessions.sendSaves(player);
                case DELETE -> TestSessions.deleteSave(player, payload.arg());
                case LOAD -> TestSessions.enterSaved(player, payload.arg());
                case MATERIALS -> TestSessions.materials(player);
                case INVITE -> TestSessions.invite(player, payload.arg());
                case DUMMY_ADD -> TestSessions.dummy(player, true);
                case DUMMY_REMOVE -> TestSessions.removeDummy(player, payload.arg());
                case SET_FLOOR -> TestSessions.setRoomBlock(player, "floor", payload.arg());
                case SET_WALL -> TestSessions.setRoomBlock(player, "wall", payload.arg());
                case SET_CEILING -> TestSessions.setRoomBlock(player, "ceiling", payload.arg());
                case DUMMY_HEALTH -> TestSessions.setDummyHealth(player, payload.arg());
                case DUMMY_RENAME -> TestSessions.renameDummy(player, payload.arg());
            }
        } catch (RuntimeException e) {
            PocketSandbox.LOGGER.error("Pocket Sandbox: test world request {} from {} failed", payload.action(),
                    player.getGameProfile().getName(), e);
        }
    }

    private static void enter(ServerPlayer player, TestEnterPayload payload) {
        SceneSnapshot room;
        try {
            room = SceneSnapshot.fromBytes(payload.data(),
                    player.level().holderLookup(net.minecraft.core.registries.Registries.BLOCK));
        } catch (java.io.IOException | RuntimeException e) {
            send(player, new TestStatusPayload(false, "pocketsandbox.test.scene_invalid"));
            return;
        }
        try {
            TestSessions.enter(player, room, payload.target());
        } catch (RuntimeException e) {
            PocketSandbox.LOGGER.error("Pocket Sandbox: test world entry of {} failed",
                    player.getGameProfile().getName(), e);
        }
    }

    static void sendSaves(ServerPlayer player, com.ufuk.pocketsandbox.testworld.net.TestSavesPayload saves) {
        if (NetworkManager.canPlayerReceive(player, com.ufuk.pocketsandbox.testworld.net.TestSavesPayload.TYPE)) {
            NetworkManager.sendToPlayer(player, saves);
        }
    }

    static void sendMaterials(ServerPlayer player, com.ufuk.pocketsandbox.testworld.net.TestMaterialsPayload list) {
        if (NetworkManager.canPlayerReceive(player, com.ufuk.pocketsandbox.testworld.net.TestMaterialsPayload.TYPE)) {
            NetworkManager.sendToPlayer(player, list);
        }
    }

    static void sendSettings(ServerPlayer player, TestSettingsPayload settings) {
        if (NetworkManager.canPlayerReceive(player, com.ufuk.pocketsandbox.testworld.net.TestSettingsSyncPayload.TYPE)) {
            NetworkManager.sendToPlayer(player, new com.ufuk.pocketsandbox.testworld.net.TestSettingsSyncPayload(settings));
        }
        // The border box goes with every settings update (entering, resizing): the client collides with it too.
        double[] box = TestSessions.borderBoxOf(player);
        if (box != null && NetworkManager.canPlayerReceive(player, com.ufuk.pocketsandbox.testworld.net.TestAreaPayload.TYPE)) {
            NetworkManager.sendToPlayer(player, new com.ufuk.pocketsandbox.testworld.net.TestAreaPayload((int) box[0],
                    (int) box[1], (int) box[2], (int) box[3], (int) box[4], (int) box[5]));
        }
    }

    /** The room as it was left, for the reopened pocket scene. */
    static void sendRoom(ServerPlayer player, byte[] room) {
        if (room.length <= TestLeftPayload.MAX_BYTES && NetworkManager.canPlayerReceive(player, TestLeftPayload.TYPE)) {
            NetworkManager.sendToPlayer(player, new TestLeftPayload(room));
        }
    }

    /** Sends only to clients that have the channel (a client without the mod never gets an unknown payload). */
    public static void send(ServerPlayer player, TestStatusPayload payload) {
        if (NetworkManager.canPlayerReceive(player, TestStatusPayload.TYPE)) {
            NetworkManager.sendToPlayer(player, payload);
        }
    }
}
