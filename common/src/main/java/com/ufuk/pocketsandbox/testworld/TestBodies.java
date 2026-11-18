package com.ufuk.pocketsandbox.testworld;

import com.ufuk.pocketsandbox.PocketSandbox;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * The player's body left in the real world while they are in the test world: an armour stand wearing their
 * head, named "Name (testing)". It holds nothing that could be taken (no copies of real items). Damage it
 * takes comes off the real profile's health; if that reaches zero the test session ends and the player dies
 * for real where the body stood. Mobs do not target armour stands: damage comes from the world and players.
 */
final class TestBodies {
    static final String TAG = "pocketsandbox_body";
    static final String OWNER_TAG = "pocketsandbox_body_owner_";
    /** Every slot: no taking, replacing or placing items. */
    private static final int ALL_SLOTS_DISABLED = 0x3F3F3F;
    /** Bodies that must go but whose chunk was not loaded when the session ended. */
    private static final List<UUID[]> pendingRemoval = new ArrayList<>();

    private TestBodies() {
    }

    static boolean isBody(Entity entity) {
        return entity instanceof ArmorStand && entity.getTags().contains(TAG);
    }

    /**
     * Builds (does not add) the body for the saved real position. The caller records its UUID in the session
     * first, then adds it: a body that is not recorded is treated as an orphan and refused.
     */
    static ArmorStand create(ServerPlayer player, SessionStore.Session session, ServerLevel realLevel) {
        ArmorStand stand = EntityType.ARMOR_STAND.create(realLevel);
        if (stand == null) {
            return null;
        }
        stand.moveTo(session.x(), session.y(), session.z(), session.yaw(), 0.0F);
        stand.setYHeadRot(session.yaw());
        ((com.ufuk.pocketsandbox.mixin.ArmorStandAccessor) stand).pocketsandbox$setDisabledSlots(ALL_SLOTS_DISABLED);
        stand.setShowArms(true);
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
        stand.setItemSlot(EquipmentSlot.HEAD, head);
        stand.setCustomName(Component.translatable("pocketsandbox.test.body.name", player.getGameProfile().getName()));
        stand.setCustomNameVisible(true);
        stand.addTag(TAG);
        stand.addTag(OWNER_TAG + player.getUUID());
        return stand;
    }

    /** Removes the body (now, or as soon as its chunk is loaded). */
    static void remove(MinecraftServer server, SessionStore.Session session) {
        String id = session.room().getString("Body");
        if (id.isEmpty()) {
            return;
        }
        try {
            UUID body = UUID.fromString(id);
            ServerLevel level = levelOf(server, session.realDimension());
            Entity entity = level == null ? null : level.getEntity(body);
            if (entity != null) {
                entity.discard();
            } else {
                pendingRemoval.add(new UUID[] {body});
            }
        } catch (IllegalArgumentException ignored) {
            // no body recorded
        }
    }

    /** Every second: bodies whose chunk has loaded since their session ended are removed. */
    static void tick(MinecraftServer server) {
        if (pendingRemoval.isEmpty() || server.getTickCount() % 20 != 0) {
            return;
        }
        pendingRemoval.removeIf(entry -> {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(entry[0]);
                if (entity != null) {
                    entity.discard();
                    return true;
                }
            }
            return false;
        });
    }

    /** A body that is loaded while its owner is not in the test world (old, orphaned) is not kept. */
    static boolean isOrphan(Entity entity) {
        if (!isBody(entity)) {
            return false;
        }
        for (String tag : entity.getTags()) {
            if (tag.startsWith(OWNER_TAG)) {
                try {
                    return !TestSessions.ownsBody(UUID.fromString(tag.substring(OWNER_TAG.length())), entity.getUUID());
                } catch (IllegalArgumentException e) {
                    return true;
                }
            }
        }
        return true;
    }

    /** Damage to a body: taken from the real profile's health; the stand itself never breaks. */
    static void hurt(ArmorStand stand, DamageSource source, float amount) {
        if (amount <= 0.0F || stand.isInvulnerableTo(source) && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        for (String tag : stand.getTags()) {
            if (tag.startsWith(OWNER_TAG)) {
                try {
                    TestSessions.bodyHurt(stand.getServer(), UUID.fromString(tag.substring(OWNER_TAG.length())), amount);
                } catch (IllegalArgumentException | NullPointerException e) {
                    PocketSandbox.LOGGER.warn("Pocket Sandbox: body damage could not be applied", e);
                }
                return;
            }
        }
    }

    static Component hurtMessage(float amount, float health) {
        return Component.translatable("pocketsandbox.test.body.hurt", String.format(java.util.Locale.ROOT, "%.1f", amount),
                String.format(java.util.Locale.ROOT, "%.1f", Math.max(0.0F, health))).withStyle(ChatFormatting.RED);
    }

    private static ServerLevel levelOf(MinecraftServer server, String dimension) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(dimension);
        if (id == null) {
            return null;
        }
        return server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
    }
}
