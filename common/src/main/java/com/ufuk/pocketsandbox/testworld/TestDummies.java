package com.ufuk.pocketsandbox.testworld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;

/**
 * Training dummies in the test world. On the server a still, silent husk (clients with Pocket Sandbox draw it as a
 * wooden training dummy; others see the husk). Its name tag shows the hits so far and the last hit (and its health
 * when it can die). Every hit is measured from the health it lost (after armour and enchantments, as the game
 * really dealt it) and reported to the attacker with the DPS of the last seconds.
 *
 * <p>Room settings: "DummyKillable" (else it is healed after every hit and never dies), "DummyHealth" (index into
 * typed health, 1..10 000, default 20), "DummyRemove" (when killable: gone for good, else back after
 * {@link #RESPAWN_TICKS}). Each dummy is named "Dummy 1", "Dummy 2"... (the lowest free number of its area) and can be
 * renamed by the player; the name stays when it comes back.
 */
final class TestDummies {
    static final String TAG = "pocketsandbox_dummy";
    static final String KILLABLE = "DummyKillable";
    static final String HEALTH = "DummyHealth";
    static final String REMOVE = "DummyRemove";
    /** Health choices when a dummy can die (half hearts: 20 = a player's 10 hearts). */
    private static final int DEFAULT_HEALTH = 20;
    /** Entity tags holding a dummy's number ("Dummy 3") or its own name. */
    private static final String NUMBER_TAG = "pocketsandbox_dummy_n:";
    private static final String NAME_TAG = "pocketsandbox_dummy_name:";
    static final int MAX_NAME = 24;
    private static final double IMMORTAL_HEALTH = 1000.0;
    private static final int MAX_PER_AREA = 10;
    private static final long RESPAWN_TICKS = 60;
    private static final long DPS_WINDOW_TICKS = 100;
    /** Hits of the last few seconds per attacker, for the DPS figure. */
    private static final Map<UUID, List<long[]>> recent = new HashMap<>();
    /** Hits taken per dummy (shown on its name tag). */
    private static final Map<UUID, Integer> hitsTaken = new HashMap<>();
    /** Dummies waiting to come back: x, y, z, yaw, game time. */
    private static final List<Respawn> respawns = new ArrayList<>();

    /** A dummy that died and comes back: where, when, and its name tags. */
    private record Respawn(double x, double y, double z, float yaw, long at, List<String> nameTags) {
    }

    private TestDummies() {
    }

    /** Dummies alive in the test level (rebuilt from a full scan every 5 s, e.g. after a server restart). */
    private static final java.util.Set<UUID> known = new java.util.HashSet<>();

    static boolean isDummy(Entity entity) {
        return entity.getTags().contains(TAG);
    }

    static boolean killable(CompoundTag room) {
        return room != null && room.getBoolean(KILLABLE);
    }

    static int health(CompoundTag room) {
        int hp = room == null ? 0 : room.getInt(HEALTH);
        return hp <= 0 ? DEFAULT_HEALTH : Math.min(com.ufuk.pocketsandbox.testworld.net.TestSettingsPayload.MAX_DUMMY_HP, hp);
    }

    /** A dummy three blocks in front of the player, kept inside the area. */
    static String add(ServerPlayer player, TestArea area) {
        ServerLevel level = player.serverLevel();
        if (count(level, area) >= MAX_PER_AREA) {
            return "pocketsandbox.test.dummy.limit";
        }
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        Vec3 ahead = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize().scale(3.0);
        double x = clamp(player.getX() + ahead.x, area.minX() + 0.5, area.maxX() + 0.5);
        double z = clamp(player.getZ() + ahead.z, area.minZ() + 0.5, area.maxZ() + 0.5);
        double y = Math.max(player.getY(), area.floorY() + 1);
        List<String> tags = List.of(NUMBER_TAG + freeNumber(level, area));
        return spawn(level, x, y, z, player.getYRot() + 180.0F, tags) ? "pocketsandbox.test.dummy.added"
                : "pocketsandbox.test.dummy.failed";
    }

    /** Lowest number not used by another dummy of the area. */
    private static int freeNumber(ServerLevel level, TestArea area) {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Entity entity : dummies(level, area)) {
            for (String tag : entity.getTags()) {
                if (tag.startsWith(NUMBER_TAG)) {
                    try {
                        used.add(Integer.parseInt(tag.substring(NUMBER_TAG.length())));
                    } catch (NumberFormatException ignored) {
                        // not ours
                    }
                }
            }
        }
        for (Respawn r : respawns) {
            for (String tag : r.nameTags()) {
                if (tag.startsWith(NUMBER_TAG) && !area.isOutside(r.x(), r.y(), r.z())) {
                    used.add(Integer.parseInt(tag.substring(NUMBER_TAG.length())));
                }
            }
        }
        int n = 1;
        while (used.contains(n)) {
            n++;
        }
        return n;
    }

    private static List<String> nameTags(Entity dummy) {
        List<String> tags = new ArrayList<>();
        for (String tag : dummy.getTags()) {
            if (tag.startsWith(NUMBER_TAG) || tag.startsWith(NAME_TAG)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    /** "Dummy 3" (translated on each client) or the name the player gave it. */
    private static Component label(Entity dummy) {
        String number = "?";
        for (String tag : dummy.getTags()) {
            if (tag.startsWith(NAME_TAG)) {
                return Component.literal(tag.substring(NAME_TAG.length()));
            }
            if (tag.startsWith(NUMBER_TAG)) {
                number = tag.substring(NUMBER_TAG.length());
            }
        }
        return Component.translatable("pocketsandbox.test.dummy.label", number);
    }

    /** The player renames one dummy of their area (an empty name gives back "Dummy N"). */
    static void rename(ServerPlayer player, TestArea area, UUID id, String rawName) {
        String name = rawName == null ? "" : rawName.replaceAll("[\\p{Cntrl}§]", "").trim();
        if (name.length() > MAX_NAME) {
            name = name.substring(0, MAX_NAME);
        }
        for (Entity entity : dummies(player.serverLevel(), area)) {
            if (entity.getUUID().equals(id) && entity instanceof LivingEntity dummy) {
                for (String tag : List.copyOf(entity.getTags())) {
                    if (tag.startsWith(NAME_TAG)) {
                        entity.removeTag(tag);
                    }
                }
                if (!name.isEmpty()) {
                    entity.addTag(NAME_TAG + name);
                }
                rename(dummy, TestSessions.roomAt(entity.getX(), entity.getZ()));
                return;
            }
        }
    }

    private static boolean spawn(ServerLevel level, double x, double y, double z, float yaw, List<String> tags) {
        Husk dummy = EntityType.HUSK.create(level);
        if (dummy == null) {
            return false;
        }
        dummy.moveTo(x, y, z, yaw, 0.0F);
        dummy.setYHeadRot(yaw);
        dummy.setYBodyRot(yaw);
        dummy.setNoAi(true);
        dummy.setSilent(true);
        dummy.setPersistenceRequired();
        dummy.addTag(TAG);
        tags.forEach(dummy::addTag);
        // No loot and no equipment drops when it dies.
        CompoundTag data = new CompoundTag();
        dummy.saveWithoutId(data);
        data.putString("DeathLootTable", "minecraft:empty");
        dummy.load(data);
        var knockback = dummy.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.setBaseValue(1.0);
        }
        applySettings(dummy, TestSessions.roomAt(x, z));
        dummy.setCustomNameVisible(true);
        level.addFreshEntity(dummy);
        known.add(dummy.getUUID());
        return true;
    }

    /** Health and name follow the room settings (also called when the player changes them). */
    static void applySettings(LivingEntity dummy, CompoundTag room) {
        var maxHealth = dummy.getAttribute(Attributes.MAX_HEALTH);
        double health = killable(room) ? health(room) : IMMORTAL_HEALTH;
        if (maxHealth != null && maxHealth.getBaseValue() != health) {
            maxHealth.setBaseValue(health);
        }
        dummy.setHealth(dummy.getMaxHealth());
        rename(dummy, room);
    }

    /** Every dummy of an area after the settings changed. */
    static void applySettings(ServerLevel level, TestArea area, CompoundTag room) {
        for (Entity entity : dummies(level, area)) {
            if (entity instanceof LivingEntity dummy) {
                applySettings(dummy, room);
            }
        }
    }

    private static void rename(LivingEntity dummy, CompoundTag room) {
        int hits = hitsTaken.getOrDefault(dummy.getUUID(), 0);
        Component label = label(dummy);
        Component name = killable(room)
                ? Component.translatable("pocketsandbox.test.dummy.name.health", label, hits,
                        String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(dummy.getHealth())),
                        String.format(java.util.Locale.ROOT, "%.0f", dummy.getMaxHealth()))
                : hits == 0 ? Component.translatable("pocketsandbox.test.dummy.name", label)
                        : Component.translatable("pocketsandbox.test.dummy.name.hits", label, hits);
        dummy.setCustomName(name);
    }

    static String removeAll(ServerPlayer player, TestArea area) {
        for (Entity entity : dummies(player.serverLevel(), area)) {
            hitsTaken.remove(entity.getUUID());
            entity.discard();
        }
        respawns.removeIf(r -> !area.isOutside(r.x(), r.y(), r.z()));
        return "pocketsandbox.test.dummy.removed";
    }

    /** One dummy of this area (no message: the list on screen shows it is gone). */
    static void removeOne(ServerPlayer player, TestArea area, UUID id) {
        for (Entity entity : dummies(player.serverLevel(), area)) {
            if (entity.getUUID().equals(id)) {
                hitsTaken.remove(id);
                lastHealth.remove(id);
                entity.discard();
                return;
            }
        }
    }

    private static int count(ServerLevel level, TestArea area) {
        return dummies(level, area).size();
    }

    private static List<Entity> dummies(ServerLevel level, TestArea area) {
        var box = new net.minecraft.world.phys.AABB(area.markerMinX(), area.bottomLimitY(), area.markerMinZ(),
                area.markerMaxX() + 1, area.markerTopY() + 1, area.markerMaxZ() + 1);
        return new ArrayList<>(level.getEntities((Entity) null, box, TestDummies::isDummy));
    }

    /** After every tick of the test level: measure lost health, report it, heal back unless it may die. */
    static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % 100 == 0) {
            known.clear();
            for (Entity entity : level.getAllEntities()) {
                if (isDummy(entity)) {
                    known.add(entity.getUUID());
                }
            }
        }
        java.util.Iterator<UUID> ids = known.iterator();
        while (ids.hasNext()) {
            Entity entity = level.getEntity(ids.next());
            if (entity == null || entity.isRemoved()) {
                ids.remove();
                continue;
            }
            if (!(entity instanceof LivingEntity dummy) || !dummy.isAlive()) {
                continue;
            }
            CompoundTag room = TestSessions.roomAt(dummy.getX(), dummy.getZ());
            float lost = lastHealth(dummy) - dummy.getHealth();
            if (lost <= 0.0F) {
                continue;
            }
            hitsTaken.merge(dummy.getUUID(), 1, Integer::sum);
            if (!killable(room)) {
                dummy.setHealth(dummy.getMaxHealth());
            }
            remember(dummy);
            rename(dummy, room);
            if (dummy.getLastHurtByMob() instanceof ServerPlayer attacker) {
                report(attacker, lost, now);
            }
        }
        Iterator<Respawn> it = respawns.iterator();
        while (it.hasNext()) {
            Respawn r = it.next();
            if (now >= r.at()) {
                it.remove();
                if (TestSessions.roomAt(r.x(), r.z()) != null) {
                    spawn(level, r.x(), r.y(), r.z(), r.yaw(), r.nameTags());
                }
            }
        }
    }

    /** Health after the last measurement (killable dummies keep losing health, so compare with it). */
    private static final Map<UUID, Float> lastHealth = new HashMap<>();

    private static float lastHealth(LivingEntity dummy) {
        return lastHealth.getOrDefault(dummy.getUUID(), dummy.getMaxHealth());
    }

    private static void remember(LivingEntity dummy) {
        lastHealth.put(dummy.getUUID(), dummy.getHealth());
    }

    private static void report(ServerPlayer attacker, float damage, long now) {
        List<long[]> hits = recent.computeIfAbsent(attacker.getUUID(), id -> new ArrayList<>());
        hits.removeIf(hit -> now - hit[0] > DPS_WINDOW_TICKS);
        hits.add(new long[] {now, Float.floatToIntBits(damage)});
        float total = 0.0F;
        long first = now;
        for (long[] hit : hits) {
            total += Float.intBitsToFloat((int) hit[1]);
            first = Math.min(first, hit[0]);
        }
        float seconds = Math.max(1.0F, (now - first) / 20.0F);
        attacker.displayClientMessage(Component.translatable("pocketsandbox.test.dummy.hit",
                String.format(java.util.Locale.ROOT, "%.1f", damage),
                String.format(java.util.Locale.ROOT, "%.1f", total / seconds), hits.size())
                .withStyle(ChatFormatting.GOLD), true);
    }

    /**
     * A dummy about to die: an immortal one is healed instead (true = cancel the death). A killable one dies; it
     * comes back at the same spot after a few seconds unless the room says "remove".
     */
    static boolean preventDeath(LivingEntity entity) {
        if (!isDummy(entity)) {
            return false;
        }
        CompoundTag room = TestSessions.roomAt(entity.getX(), entity.getZ());
        if (!killable(room)) {
            entity.setHealth(entity.getMaxHealth());
            return true;
        }
        float lost = lastHealth(entity);
        if (entity.getLastHurtByMob() instanceof ServerPlayer attacker && lost > 0.0F) {
            report(attacker, lost, entity.level().getGameTime());
            attacker.displayClientMessage(Component.translatable("pocketsandbox.test.dummy.killed",
                    hitsTaken.getOrDefault(entity.getUUID(), 0) + 1).withStyle(ChatFormatting.RED), false);
        }
        hitsTaken.remove(entity.getUUID());
        lastHealth.remove(entity.getUUID());
        if (!room.getBoolean(REMOVE)) {
            respawns.add(new Respawn(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(),
                    entity.level().getGameTime() + RESPAWN_TICKS, nameTags(entity)));
        }
        return false;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
