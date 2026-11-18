package com.ufuk.pocketsandbox.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BallisticsTest {
    private static final double EYE = 1.62;

    @Test
    void flatBowShotLandsAndDropsBelowEyeHeight() {
        Ballistics.Flight flight = Ballistics.simulate(EYE, 3.0, 0.0, 0.05, 0.99, 0.0);
        assertTrue(flight.landed());
        assertTrue(flight.distance() > 10 && flight.distance() < 30, "flat full bow shot ~ 20 blocks: " + flight.distance());
        double[] last = flight.points().get(flight.points().size() - 1);
        assertEquals(0.0, last[1], 1e-9);
    }

    @Test
    void angledShotFliesFurtherThanFlat() {
        double flat = Ballistics.simulate(EYE, 3.0, 0.0, 0.05, 0.99, 0.0).distance();
        double max = Ballistics.maxRange(EYE, 3.0, 0.05, 0.99, 0.0);
        assertTrue(max > flat * 3, "max " + max + " vs flat " + flat);
        assertTrue(max > 80 && max < 140, "full bow max range is roughly 100 blocks: " + max);
    }

    @Test
    void slowerProjectilesFallShorter() {
        double bow = Ballistics.maxRange(EYE, 3.0, 0.05, 0.99, 0.0);
        double snowball = Ballistics.maxRange(EYE, 1.5, 0.03, 0.99, 0.0);
        assertTrue(snowball < bow);
    }

    @Test
    void neverLandingIsReported() {
        Ballistics.Flight flight = Ballistics.simulate(EYE, 1.0, 45.0, 0.0, 1.0, 0.0);
        assertFalse(flight.landed());
        assertEquals(Ballistics.MAX_TICKS, flight.ticks());
    }

    @Test
    void arrowDamageMatchesVanilla() {
        // Full bow, point blank: speed 3 * base 2 = 6.
        assertArrayEquals(new int[] {6, 6}, Ballistics.arrowDamage(3.0, 2.0, false));
        assertArrayEquals(new int[] {6, 10}, Ballistics.arrowDamage(3.0, 2.0, true));
        assertArrayEquals(new int[] {5, 5}, Ballistics.arrowDamage(2.4, 2.0, false));
    }

    @Test
    void speedDropsWithDragAndGravity() {
        double after = Ballistics.speedAfter(3.0, 0.0, 0.05, 0.99, 10);
        assertTrue(after < 3.0);
        assertEquals(3.0, Ballistics.speedAfter(3.0, 0.0, 0.0, 1.0, 10), 1e-9);
    }
}
