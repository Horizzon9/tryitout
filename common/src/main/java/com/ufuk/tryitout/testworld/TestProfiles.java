package com.ufuk.tryitout.testworld;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Builds and applies player profiles. The real profile is the player's full NBT ({@code saveWithoutId}: inventory,
 * ender chest, health, food, XP, effects, game mode, spawn point, mod data attached to the player). Applying a
 * profile loads that NBT into the live player; the dimension change that follows re-sends everything to the client.
 */
public final class TestProfiles {
    private TestProfiles() {
    }

    public static CompoundTag capture(ServerPlayer player) {
        return player.saveWithoutId(new CompoundTag());
    }

    /**
     * A fresh "second character": built from the real NBT so the entity keeps its identity, with everything a
     * player carries or has reset: empty inventory and ender chest, full health and food, no XP, no effects,
     * Creative. Mod data attached to the player is copied (B7 decides per mod); none of it ever flows back,
     * because leaving loads the saved real NBT over the player.
     */
    public static CompoundTag blankTestProfile(CompoundTag real, float maxHealth) {
        CompoundTag tag = real.copy();
        tag.put("Inventory", new ListTag());
        tag.put("EnderItems", new ListTag());
        tag.remove("active_effects");
        tag.remove("ActiveEffects");
        tag.putFloat("Health", maxHealth);
        tag.putInt("foodLevel", 20);
        tag.putFloat("foodSaturationLevel", 5.0F);
        tag.putFloat("foodExhaustionLevel", 0.0F);
        tag.putInt("foodTickTimer", 0);
        tag.putInt("XpLevel", 0);
        tag.putFloat("XpP", 0.0F);
        tag.putInt("XpTotal", 0);
        tag.putInt("Score", 0);
        tag.putShort("Fire", (short) 0);
        tag.putShort("Air", (short) 300);
        tag.putFloat("FallDistance", 0.0F);
        tag.putInt("SelectedItemSlot", 0);
        tag.putInt("playerGameType", GameType.CREATIVE.getId());
        tag.putInt("previousPlayerGameType", -1);
        tag.remove("RootVehicle");
        tag.remove("SleepingX");
        tag.remove("SleepingY");
        tag.remove("SleepingZ");
        return tag;
    }

    /** Loads {@code profile} into the live player (position comes from the teleport that follows). */
    public static void apply(ServerPlayer player, CompoundTag profile) {
        player.stopRiding();
        player.removeAllEffects();
        player.load(profile);
        // Each profile has its own game mode (creative in the test world never carries over): set it explicitly
        // so the client and the abilities follow at once, whatever path the teleport takes.
        if (profile.contains("playerGameType", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
            GameType mode = GameType.byId(profile.getInt("playerGameType"));
            if (player.gameMode.getGameModeForPlayer() != mode) {
                player.setGameMode(mode);
            }
        }
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.fallDistance = 0.0F;
    }
}
