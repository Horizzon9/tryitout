package com.ufuk.pocketsandbox.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatMathTest {
    private static final double EPS = 1e-9;

    @Test
    void diamondSwordCooldownIsTwelveAndAHalfTicks() {
        // Diamond sword: attack speed 4 - 2.4 = 1.6.
        assertEquals(12.5, CombatMath.cooldownTicks(1.6), EPS);
        assertEquals(1.0, CombatMath.charge(20, 1.6), EPS);
        assertEquals(0.5 / 12.5, CombatMath.charge(0, 1.6), EPS);
    }

    @Test
    void fullChargeHitsForFullDamage() {
        // Diamond sword: 1 + 6 = 7 damage; Sharpness V adds 3.
        assertEquals(7.0, CombatMath.damage(7.0, 0.0, 1.0, false), EPS);
        assertEquals(10.0, CombatMath.damage(7.0, 3.0, 1.0, false), EPS);
    }

    @Test
    void spammingDealsAFifthOfTheBase() {
        assertEquals(7.0 * 0.2, CombatMath.damage(7.0, 0.0, 0.0, false), EPS);
        double half = CombatMath.damage(7.0, 3.0, 0.5, false);
        assertEquals(7.0 * (0.2 + 0.25 * 0.8) + 3.0 * 0.5, half, EPS);
    }

    @Test
    void criticalNeedsAlmostFullCharge() {
        assertEquals(7.0 * 1.5, CombatMath.damage(7.0, 0.0, 1.0, true), EPS);
        assertTrue(CombatMath.damage(7.0, 0.0, 0.5, true) < 7.0, "no crit at half charge");
        assertEquals(7.0 * 1.5 + 3.0, CombatMath.damage(7.0, 3.0, 1.0, true), EPS);
    }

    @Test
    void dpsAndZeroSpeed() {
        assertEquals(11.2, CombatMath.dps(7.0, 1.6), 1e-6);
        assertEquals(0.0, CombatMath.dps(7.0, 0.0), EPS);
        assertEquals(0.0, CombatMath.charge(100, 0.0), EPS);
        assertTrue(Double.isInfinite(CombatMath.cooldownTicks(0)));
    }
}
