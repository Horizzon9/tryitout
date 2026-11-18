package com.ufuk.pocketsandbox.testworld;

import com.ufuk.pocketsandbox.testworld.net.TestSettingsPayload;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Time and weather of the test dimension, separate from the overworld (extra dimensions normally share the
 * overworld's). Mixins route the test level's day time and weather here; nothing in the real dimensions
 * changes. The test dimension is shared by every test player, so on a server the last change wins.
 * Defaults: fixed noon, clear sky.
 */
public final class TestClock {
    private static boolean active;
    private static long dayTime = TestSettingsPayload.TIMES[0];
    private static boolean cycle;
    private static int weather;

    private TestClock() {
    }

    /** True while a server runs (the integrated one too); the client of a remote server never activates it. */
    public static boolean active() {
        return active;
    }

    public static long dayTime() {
        return dayTime;
    }

    public static int timeSetting() {
        if (cycle) {
            return TestSettingsPayload.TIME_CYCLE;
        }
        for (int i = 0; i < TestSettingsPayload.TIMES.length; i++) {
            if (TestSettingsPayload.TIMES[i] == dayTime % 24000L) {
                return i;
            }
        }
        return 0;
    }

    public static int weather() {
        return weather;
    }

    static void onServerStarted() {
        active = true;
        dayTime = TestSettingsPayload.TIMES[0];
        cycle = false;
        weather = 0;
    }

    static void onServerStopped() {
        active = false;
    }

    static void setTime(MinecraftServer server, int setting) {
        cycle = setting == TestSettingsPayload.TIME_CYCLE;
        if (!cycle) {
            dayTime = TestSettingsPayload.TIMES[Math.max(0, Math.min(TestSettingsPayload.TIMES.length - 1, setting))];
        }
        sendTime(server);
    }

    static void setWeather(MinecraftServer server, int value) {
        weather = Math.floorMod(value, TestSettingsPayload.WEATHERS);
        ServerLevel level = server.getLevel(TestWorld.DIMENSION);
        if (level == null) {
            return;
        }
        float rain = weather >= 1 ? 1.0F : 0.0F;
        float thunder = weather == 2 ? 1.0F : 0.0F;
        level.setRainLevel(rain);
        level.setThunderLevel(thunder);
        var players = server.getPlayerList();
        players.broadcastAll(new ClientboundGameEventPacket(weather >= 1 ? ClientboundGameEventPacket.START_RAINING
                : ClientboundGameEventPacket.STOP_RAINING, 0.0F), TestWorld.DIMENSION);
        players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain),
                TestWorld.DIMENSION);
        players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder),
                TestWorld.DIMENSION);
    }

    /** Every server tick: advance the day when the cycle is on; once a second, tell test players our time. */
    static void onServerTick(MinecraftServer server) {
        if (cycle) {
            dayTime++;
        }
        if (server.getTickCount() % 20 == 0) {
            sendTime(server);
        }
    }

    private static void sendTime(MinecraftServer server) {
        ServerLevel level = server.getLevel(TestWorld.DIMENSION);
        if (level != null) {
            // Sent after vanilla's own time packet, so the client follows our cycle flag, not the gamerule.
            server.getPlayerList().broadcastAll(new ClientboundSetTimePacket(level.getGameTime(), dayTime, cycle),
                    TestWorld.DIMENSION);
        }
    }
}
