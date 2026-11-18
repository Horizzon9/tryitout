package com.ufuk.pocketsandbox.combat;

/**
 * Vanilla 1.21.1 melee formulas (Player#attack), pure so they are unit tested:
 * cooldown = 20 / attack speed ticks; charge = clamp((ticks since last swing + 0.5) / cooldown);
 * base damage scales by {@code 0.2 + 0.8 * charge^2}, enchantment bonus by {@code charge};
 * a critical hit (charge above 0.9 while falling) multiplies the scaled base damage by 1.5.
 */
public final class CombatMath {
    public static final double CRIT_MULTIPLIER = 1.5;
    public static final double CRIT_MIN_CHARGE = 0.9;

    private CombatMath() {
    }

    /** Ticks for a full swing; infinite for speed 0 or less. */
    public static double cooldownTicks(double attackSpeed) {
        return attackSpeed <= 0 ? Double.POSITIVE_INFINITY : 20.0 / attackSpeed;
    }

    /** Attack strength (0..1) after {@code ticksSinceSwing} ticks. */
    public static double charge(long ticksSinceSwing, double attackSpeed) {
        double cooldown = cooldownTicks(attackSpeed);
        if (Double.isInfinite(cooldown)) {
            return 0.0;
        }
        return clamp((ticksSinceSwing + 0.5) / cooldown);
    }

    /** Damage dealt with {@code baseDamage} (attribute value) and {@code enchantBonus} at {@code charge}. */
    public static double damage(double baseDamage, double enchantBonus, double charge, boolean critical) {
        double c = clamp(charge);
        double base = baseDamage * (0.2 + c * c * 0.8);
        if (critical && c > CRIT_MIN_CHARGE) {
            base *= CRIT_MULTIPLIER;
        }
        return base + enchantBonus * c;
    }

    /** Damage per second when every swing waits for a full charge. */
    public static double dps(double fullDamage, double attackSpeed) {
        return attackSpeed <= 0 ? 0.0 : fullDamage * attackSpeed;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
