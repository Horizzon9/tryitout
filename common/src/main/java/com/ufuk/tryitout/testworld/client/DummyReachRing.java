package com.ufuk.tryitout.testworld.client;

import com.ufuk.tryitout.combat.Ballistics;
import com.ufuk.tryitout.combat.RangedProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Inside the test world: a ring on the ground around every training dummy showing from how far the held item
 * can hit it (melee: the real reach to the dummy's box, modded reach included; ranged: the longest flat-ground
 * shot), and a clear sign above the hotbar and health bars: "In reach" (green, the ring turns green too) or how many blocks
 * are missing. Drawn with client particles and the HUD, so it follows the held item at once.
 */
public final class DummyReachRing {
    private static final DustParticleOptions MELEE = new DustParticleOptions(new Vector3f(0.33F, 0.67F, 1.0F), 1.0F);
    private static final DustParticleOptions RANGED = new DustParticleOptions(new Vector3f(1.0F, 0.8F, 0.2F), 1.0F);
    private static final DustParticleOptions IN_REACH = new DustParticleOptions(new Vector3f(0.3F, 1.0F, 0.45F), 1.2F);
    /** Half a dummy's width: melee reach is measured to its box, not its centre. */
    private static final double DUMMY_HALF_WIDTH = 0.3;
    private static final double MAX_RADIUS = 64.0;
    private static final double SEARCH = 64.0;
    /** The hint shows only while a dummy is this close (no clutter elsewhere in the area). */
    private static final double HINT_DISTANCE = 24.0;

    private static ItemStack cachedItem = ItemStack.EMPTY;
    private static double cachedRangedRadius;
    /** Nearest dummy: horizontal distance minus reach (<= 0 means in reach); NaN when none is near. */
    private static double margin = Double.NaN;
    private static boolean rangedHeld;

    private DummyReachRing() {
    }

    static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        margin = Double.NaN;
        if (player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        RangedProfile ranged = RangedProfile.of(held, player);
        rangedHeld = ranged != null;
        double radius;
        if (ranged != null) {
            if (!ItemStack.isSameItemSameComponents(held, cachedItem)) {
                cachedItem = held.copy();
                cachedRangedRadius = Ballistics.maxRange(player.getEyeHeight(), ranged.speed(), ranged.gravity(),
                        ranged.drag(), 0.0);
            }
            radius = cachedRangedRadius;
        } else {
            radius = player.entityInteractionRange() + DUMMY_HALF_WIDTH;
        }
        radius = Math.min(radius, MAX_RADIUS);
        if (radius <= 0.0) {
            return;
        }
        AABB around = player.getBoundingBox().inflate(SEARCH);
        double best = Double.MAX_VALUE;
        java.util.List<Entity> dummies = mc.level.getEntities(player, around, DummyReachRing::isDummy);
        for (Entity entity : dummies) {
            double dx = entity.getX() - player.getX();
            double dz = entity.getZ() - player.getZ();
            best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
        }
        if (best <= HINT_DISTANCE + radius) {
            margin = best - radius;
        }
        if (player.tickCount % 2 != 0) {
            return;
        }
        for (Entity entity : dummies) {
            double dx = entity.getX() - player.getX();
            double dz = entity.getZ() - player.getZ();
            boolean inside = Math.sqrt(dx * dx + dz * dz) <= radius;
            ring(mc, entity, radius, inside ? IN_REACH : ranged != null ? RANGED : MELEE);
        }
    }

    /** "In reach" or "N blocks too far" above the hotbar and health bars, while a dummy is near. */
    static void renderHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (Double.isNaN(margin) || mc.options.hideGui || mc.screen != null) {
            return;
        }
        Component text;
        int color;
        if (margin <= 0.0) {
            text = Component.translatable(rangedHeld ? "tryitout.dummy.reach.ranged_in" : "tryitout.dummy.reach.in");
            color = 0xFF55FF77;
        } else {
            text = Component.translatable("tryitout.dummy.reach.out",
                    String.format(java.util.Locale.ROOT, "%.1f", margin));
            color = 0xFFFF7766;
        }
        int x = graphics.guiWidth() / 2 - mc.font.width(text) / 2;
        // Above the hotbar, the health / armour rows, the held item name and the action bar (hit damage shows there).
        int y = graphics.guiHeight() - 84;
        graphics.fill(x - 3, y - 2, x + mc.font.width(text) + 3, y + 9, 0x90000000);
        graphics.drawString(mc.font, text, x, y, color, false);
    }

    /** Dummies are husks named with our translation keys (entity tags are not sent to clients). */
    public static boolean isDummy(Entity entity) {
        if (!(entity instanceof Husk) || !entity.hasCustomName()) {
            return false;
        }
        Component name = entity.getCustomName();
        return name != null && name.getContents() instanceof TranslatableContents contents
                && contents.getKey().startsWith("tryitout.test.dummy.name");
    }

    private static void ring(Minecraft mc, Entity dummy, double radius, DustParticleOptions dust) {
        int points = (int) Math.max(24, Math.min(160, Math.round(2.0 * Math.PI * radius / 0.6)));
        double y = dummy.getY() + 0.1;
        double phase = (dummy.tickCount % 20) / 20.0 * (2.0 * Math.PI / points);
        for (int i = 0; i < points; i++) {
            double angle = phase + i * 2.0 * Math.PI / points;
            mc.level.addParticle(dust, dummy.getX() + Math.cos(angle) * radius, y,
                    dummy.getZ() + Math.sin(angle) * radius, 0.0, 0.0, 0.0);
        }
    }
}
