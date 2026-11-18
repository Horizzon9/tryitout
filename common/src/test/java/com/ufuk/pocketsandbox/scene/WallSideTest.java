package com.ufuk.pocketsandbox.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ufuk.pocketsandbox.world.SandboxBounds;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WallSideTest {
    @Test
    void wallStandsBesideTheFloorAndIsSquare() {
        for (int size : SandboxBounds.SIZES) {
            SandboxBounds bounds = SandboxBounds.ofSize(size);
            List<int[]> wall = WallSide.positions(EnumSet.of(WallSide.NORTH), bounds);
            // floor layer + size blocks above it (big floors: up to the room height)
            int rows = Math.min(size, bounds.maxY()) + 1;
            assertEquals(size * rows, wall.size(), "size " + size);
            for (int[] p : wall) {
                assertEquals(bounds.minZ() - 1, p[2]);
                assertTrue(p[1] >= 0 && p[1] <= size && p[1] <= bounds.maxY());
                assertFalse(bounds.contains(p[0], p[1], p[2]), "wall must not be on the floor");
            }
        }
    }

    @Test
    void eachSideUsesItsEdge() {
        SandboxBounds bounds = SandboxBounds.ofSize(7);
        assertEquals(bounds.maxX() + 1, WallSide.positions(EnumSet.of(WallSide.EAST), bounds).get(0)[0]);
        assertEquals(bounds.minX() - 1, WallSide.positions(EnumSet.of(WallSide.WEST), bounds).get(0)[0]);
        assertEquals(bounds.maxZ() + 1, WallSide.positions(EnumSet.of(WallSide.SOUTH), bounds).get(0)[2]);
        assertTrue(WallSide.positions(EnumSet.noneOf(WallSide.class), bounds).isEmpty());
    }

    @Test
    void neighbouringWallsShareOneCornerColumn() {
        SandboxBounds bounds = SandboxBounds.ofSize(5);
        List<int[]> wall = WallSide.positions(EnumSet.of(WallSide.NORTH, WallSide.WEST), bounds);
        Set<String> unique = new HashSet<>();
        for (int[] p : wall) {
            assertTrue(unique.add(p[0] + "," + p[1] + "," + p[2]), "duplicate block");
        }
        int layers = 6;
        assertEquals(layers * (5 + 5 + 1), wall.size());
        assertTrue(unique.contains((bounds.minX() - 1) + ",3," + (bounds.minZ() - 1)));
        // opposite walls have no corner between them
        assertEquals(layers * 10, WallSide.positions(EnumSet.of(WallSide.NORTH, WallSide.SOUTH), bounds).size());
    }
}
