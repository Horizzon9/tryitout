package com.ufuk.pocketsandbox.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import org.jetbrains.annotations.Nullable;

/**
 * How an item shoots, with vanilla numbers: launch speed at full power (blocks/tick), gravity, drag, base
 * damage and charge time. Modded items that extend the vanilla classes are treated like them and marked
 * {@code estimated}, because a mod may change its own speed or damage.
 */
public record RangedProfile(Kind kind, double speed, double gravity, double drag, double baseDamage,
        int fullChargeTicks, boolean estimated) {

    public enum Kind {
        BOW, CROSSBOW, TRIDENT, THROWABLE, POTION
    }

    @Nullable
    public static RangedProfile of(ItemStack stack, LivingEntity holder) {
        Item item = stack.getItem();
        if (item instanceof CrossbowItem) {
            return new RangedProfile(Kind.CROSSBOW, 3.15, 0.05, 0.99, 2.0,
                    CrossbowItem.getChargeDuration(stack, holder), item.getClass() != CrossbowItem.class);
        }
        if (item instanceof BowItem) {
            return new RangedProfile(Kind.BOW, 3.0, 0.05, 0.99, 2.0, 20, item.getClass() != BowItem.class);
        }
        if (item instanceof TridentItem) {
            return new RangedProfile(Kind.TRIDENT, 2.5, 0.05, 0.99, 8.0, 10, item.getClass() != TridentItem.class);
        }
        if (item instanceof ThrowablePotionItem) {
            return new RangedProfile(Kind.POTION, 0.5, 0.05, 0.99, 0.0, 0, false);
        }
        if (item instanceof SnowballItem || item instanceof EggItem || item instanceof EnderpearlItem) {
            return new RangedProfile(Kind.THROWABLE, 1.5, 0.03, 0.99, 0.0, 0, false);
        }
        if (item instanceof ProjectileWeaponItem) {
            return new RangedProfile(Kind.BOW, 3.0, 0.05, 0.99, 2.0, 20, true);
        }
        return null;
    }

    /** True when the item is drawn / charged before it fires (bow, crossbow, trident). */
    public boolean charges() {
        return kind == Kind.BOW || kind == Kind.CROSSBOW || kind == Kind.TRIDENT;
    }

    /** Launch power 0..1 after holding for {@code ticks}. */
    public double power(int ticks) {
        return switch (kind) {
            case BOW -> BowItem.getPowerForTime(ticks);
            case CROSSBOW, TRIDENT -> ticks >= fullChargeTicks ? 1.0 : 0.0;
            case THROWABLE, POTION -> 1.0;
        };
    }

    /** Critical arrows: a fully drawn bow, and every crossbow arrow. */
    public boolean critical(double power) {
        return (kind == Kind.BOW && power >= 1.0) || kind == Kind.CROSSBOW;
    }

    /** Damage range on impact at {@code impactSpeed}. */
    public int[] damage(double impactSpeed, double power) {
        return switch (kind) {
            case BOW, CROSSBOW -> Ballistics.arrowDamage(impactSpeed, baseDamage, critical(power));
            case TRIDENT -> new int[] {(int) baseDamage, (int) baseDamage};
            case THROWABLE, POTION -> new int[] {0, 0};
        };
    }
}
