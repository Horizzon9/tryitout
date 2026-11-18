package com.ufuk.pocketsandbox.scene;

import com.ufuk.pocketsandbox.world.SandboxBounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An edge of the floor that can carry a test wall (for vines, moss, paintings, item frames, torches...). The
 * wall stands just outside the floor, beside it (not on it), one block thick, as wide as the floor and from
 * the floor layer up to {@code size} blocks above it: 5x5 above a 5x5 floor, 7x7 above 7x7, 9x9 above 9x9.
 * Several sides may be chosen; two neighbouring walls are joined by a corner column. Pure: positions are
 * plain int triples.
 */
public enum WallSide {
    NORTH, EAST, SOUTH, WEST;

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** {x, y, z} of every wall block of {@code sides} for {@code bounds}, corners included once. */
    public static List<int[]> positions(Set<WallSide> sides, SandboxBounds bounds) {
        List<int[]> list = new ArrayList<>();
        if (sides.isEmpty()) {
            return list;
        }
        int north = bounds.minZ() - 1;
        int south = bounds.maxZ() + 1;
        int west = bounds.minX() - 1;
        int east = bounds.maxX() + 1;
        for (int y = bounds.minY(); y <= bounds.minY() + bounds.size() && y <= bounds.maxY(); y++) {
            for (int i = -bounds.half(); i <= bounds.half(); i++) {
                if (sides.contains(NORTH)) {
                    list.add(new int[] {i, y, north});
                }
                if (sides.contains(SOUTH)) {
                    list.add(new int[] {i, y, south});
                }
                if (sides.contains(WEST)) {
                    list.add(new int[] {west, y, i});
                }
                if (sides.contains(EAST)) {
                    list.add(new int[] {east, y, i});
                }
            }
            corner(list, sides, NORTH, WEST, west, y, north);
            corner(list, sides, NORTH, EAST, east, y, north);
            corner(list, sides, SOUTH, WEST, west, y, south);
            corner(list, sides, SOUTH, EAST, east, y, south);
        }
        return list;
    }

    private static void corner(List<int[]> list, Set<WallSide> sides, WallSide a, WallSide b, int x, int y, int z) {
        if (sides.contains(a) && sides.contains(b)) {
            list.add(new int[] {x, y, z});
        }
    }
}
