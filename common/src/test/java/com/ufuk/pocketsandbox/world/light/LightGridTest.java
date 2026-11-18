package com.ufuk.pocketsandbox.world.light;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LightGridTest {
    private static final int S = 9;

    private static int idx(int x, int y, int z) {
        return (y * S + z) * S + x;
    }

    @Test
    void torchFallsOffByOnePerBlockInOpenAir() {
        int[] emission = new int[S * S * S];
        int[] opacity = new int[S * S * S];
        emission[idx(4, 4, 4)] = 14;
        LightGrid grid = LightGrid.compute(S, S, S, emission, opacity);
        assertEquals(14, grid.block(4, 4, 4));
        assertEquals(13, grid.block(5, 4, 4));
        assertEquals(12, grid.block(5, 5, 4));
        assertEquals(10, grid.block(8, 4, 4));
    }

    @Test
    void opaqueWallStopsBlockLight() {
        int[] emission = new int[S * S * S];
        int[] opacity = new int[S * S * S];
        emission[idx(2, 4, 4)] = 15;
        // Full wall at x = 4 from floor to ceiling.
        for (int y = 0; y < S; y++) {
            for (int z = 0; z < S; z++) {
                opacity[idx(4, y, z)] = 15;
            }
        }
        LightGrid grid = LightGrid.compute(S, S, S, emission, opacity);
        assertEquals(0, grid.block(4, 4, 4));
        assertEquals(0, grid.block(6, 4, 4));
        assertEquals(13, grid.block(3, 5, 4));
    }

    @Test
    void skyFallsStraightDownUntilRoof() {
        int[] emission = new int[S * S * S];
        int[] opacity = new int[S * S * S];
        LightGrid open = LightGrid.compute(S, S, S, emission, opacity);
        assertEquals(15, open.sky(4, 0, 4));
        // Roof one layer high covering the middle, sides open: light under it comes in from the sides.
        for (int z = 1; z < S - 1; z++) {
            for (int x = 1; x < S - 1; x++) {
                opacity[idx(x, 5, z)] = 15;
            }
        }
        LightGrid roofed = LightGrid.compute(S, S, S, emission, opacity);
        assertEquals(0, roofed.sky(4, 5, 4));
        assertEquals(15, roofed.sky(0, 2, 4), "edge column is open sky");
        assertEquals(11, roofed.sky(4, 2, 4), "4 steps in from the open edge");
    }

    @Test
    void outsideTheBoxIsOpenSkyAndDark() {
        LightGrid grid = LightGrid.compute(2, 2, 2, new int[8], new int[8]);
        assertEquals(15, grid.sky(-1, 0, 0));
        assertEquals(0, grid.block(-1, 0, 0));
    }

    @Test
    void wrongArraySizeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LightGrid.compute(2, 2, 2, new int[7], new int[8]));
    }
}
