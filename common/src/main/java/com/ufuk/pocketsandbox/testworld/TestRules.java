package com.ufuk.pocketsandbox.testworld;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

/**
 * Game rules of the test dimension: a copy of the server's rules with the test world's own switches on top, so
 * nothing changes in the real dimensions. A mixin hands this copy to the test level ({@code Level#getGameRules}).
 * Defaults: no mob spawning, no fire spread, no weather cycle, no patrols / traders / wardens / phantoms,
 * random ticks on, keep inventory on. Shared by every test player (like time and weather).
 */
public final class TestRules {
    private static boolean active;
    private static boolean mobSpawning;
    private static boolean fireSpread;
    private static boolean randomTicks = true;
    private static boolean keepInventory = true;
    private static GameRules rules;
    private static long builtAt = Long.MIN_VALUE;

    private TestRules() {
    }

    static void onServerStarted() {
        active = true;
        mobSpawning = false;
        fireSpread = false;
        randomTicks = true;
        keepInventory = true;
        rules = null;
    }

    static void onServerStopped() {
        active = false;
        rules = null;
    }

    public static boolean active() {
        return active;
    }

    public static boolean mobSpawning() {
        return mobSpawning;
    }

    public static boolean fireSpread() {
        return fireSpread;
    }

    public static boolean randomTicks() {
        return randomTicks;
    }

    public static boolean keepInventory() {
        return keepInventory;
    }

    static void set(boolean mobs, boolean fire, boolean ticks, boolean keep) {
        mobSpawning = mobs;
        fireSpread = fire;
        randomTicks = ticks;
        keepInventory = keep;
        rules = null;
    }

    /** The test level's rules; rebuilt after a change and every few seconds (the server's own rules may change). */
    public static GameRules rules(MinecraftServer server) {
        long now = server.getTickCount();
        if (rules == null || now - builtAt > 100 || now < builtAt) {
            GameRules copy = server.getGameRules().copy();
            // null server: these rules have no change callbacks that would reach the real game.
            copy.getRule(GameRules.RULE_DOMOBSPAWNING).set(mobSpawning, null);
            copy.getRule(GameRules.RULE_DOFIRETICK).set(fireSpread, null);
            copy.getRule(GameRules.RULE_RANDOMTICKING).set(randomTicks ? 3 : 0, null);
            copy.getRule(GameRules.RULE_KEEPINVENTORY).set(keepInventory, null);
            copy.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            copy.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, null);
            copy.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, null);
            copy.getRule(GameRules.RULE_DO_WARDEN_SPAWNING).set(false, null);
            copy.getRule(GameRules.RULE_DOINSOMNIA).set(false, null);
            copy.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
            rules = copy;
            builtAt = now;
        }
        return rules;
    }
}
