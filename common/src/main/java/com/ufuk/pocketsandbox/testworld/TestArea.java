package com.ufuk.pocketsandbox.testworld;

/**
 * One player's test area inside the test dimension: a square floor of {@code size} at {@code floorY}, room
 * for {@code size} blocks above it. Areas sit {@link #SPACING} blocks apart so nothing (trains, chunk loaders,
 * thrown items) can reach another player's area. Pure value; unit tested.
 *
 * <p>The teleport border is visible: the edges of a box of marker blocks {@link #LEAVE_MARGIN} blocks outside the
 * floor (and the wall ring), {@link #LEAVE_MARGIN} below the floor and above the ceiling layer, on all six sides
 * (with a cross on the top and bottom faces). Passing beyond the ring, below the bottom face or above the top
 * face puts the player back in the centre.
 */
public record TestArea(int centerX, int floorY, int centerZ, int size) {
    public static final int SPACING = 10_000;
    public static final int FLOOR_Y = 64;
    /** Empty blocks between the wall ring and the marker ring. */
    public static final int LEAVE_MARGIN = 5;
    /**
     * Floor sizes of a test area: the pocket scene's sizes plus two big ones for large builds (odd, so the floor
     * has a centre block; shown as 51 x 51 and 101 x 101).
     */
    public static final int[] SIZES = {5, 7, 9, 15, 25, 51, 101};

    /** The size in {@link #SIZES} after (step 1) or before (step -1) {@code size}, wrapping around. */
    public static int stepSize(int size, int step) {
        int index = 0;
        for (int i = 0; i < SIZES.length; i++) {
            if (SIZES[i] == size) {
                index = i;
            }
        }
        return SIZES[Math.floorMod(index + step, SIZES.length)];
    }

    public static TestArea forSlot(int slot, int size) {
        // Centre of a chunk, so the area spans as few chunks as possible.
        return new TestArea(slot * SPACING + 8, FLOOR_Y, 8, size);
    }

    public int half() {
        return size / 2;
    }

    public int minX() {
        return centerX - half();
    }

    public int maxX() {
        return centerX - half() + size - 1;
    }

    public int minZ() {
        return centerZ - half();
    }

    public int maxZ() {
        return centerZ - half() + size - 1;
    }

    /** Highest block layer that belongs to the area (room for walls and a ceiling on top). */
    public int maxY() {
        return floorY + size + 1;
    }

    /** Marker ring: x / z of the blocks on each side (wall ring + margin + 1). */
    public int markerMinX() {
        return minX() - 1 - LEAVE_MARGIN - 1;
    }

    public int markerMaxX() {
        return maxX() + 1 + LEAVE_MARGIN + 1;
    }

    public int markerMinZ() {
        return minZ() - 1 - LEAVE_MARGIN - 1;
    }

    public int markerMaxZ() {
        return maxZ() + 1 + LEAVE_MARGIN + 1;
    }

    /** Top block of the corner pillars: the upper limit. */
    public int markerTopY() {
        return maxY() + LEAVE_MARGIN;
    }

    /** Lowest y a player may reach (below it: back to the centre). */
    public int bottomLimitY() {
        return floorY - LEAVE_MARGIN;
    }

    /** True when a player at (x, y, z) has passed the marker ring, the bottom limit or the pillar tops. */
    public boolean isOutside(double x, double y, double z) {
        return x < markerMinX() || x >= markerMaxX() + 1
                || z < markerMinZ() || z >= markerMaxZ() + 1
                || y < bottomLimitY() || y >= markerTopY() + 1;
    }

    /**
     * True for a marker block: the edges of the border box on all six sides (a ring at the bottom limit, one at
     * the top limit, the four vertical corner edges).
     */
    public boolean isMarker(int x, int y, int z) {
        if (y < bottomLimitY() || y > markerTopY()) {
            return false;
        }
        boolean onRingX = x == markerMinX() || x == markerMaxX();
        boolean onRingZ = z == markerMinZ() || z == markerMaxZ();
        boolean inRingX = x >= markerMinX() && x <= markerMaxX();
        boolean inRingZ = z >= markerMinZ() && z <= markerMaxZ();
        boolean onPerimeter = (onRingX && inRingZ) || (onRingZ && inRingX);
        if (onPerimeter && (y == bottomLimitY() || y == markerTopY())) {
            return true;
        }
        // Top and bottom faces also get a cross through the centre, so the limit is visible from inside.
        boolean cap = y == bottomLimitY() || y == markerTopY();
        if (cap && inRingX && inRingZ && (x == centerX || z == centerZ)) {
            return true;
        }
        return onRingX && onRingZ;
    }

    /**
     * Build box: everything inside the marker border (the player may build anywhere there, also around and
     * below the floor). Saves, the material list and the room taken back to the pocket scene use this box.
     */
    public int buildMinX() {
        return markerMinX() + 1;
    }

    public int buildMaxX() {
        return markerMaxX() - 1;
    }

    public int buildMinZ() {
        return markerMinZ() + 1;
    }

    public int buildMaxZ() {
        return markerMaxZ() - 1;
    }

    public int buildMinY() {
        return bottomLimitY() + 1;
    }

    public int buildMaxY() {
        return markerTopY() - 1;
    }

    /**
     * True on the six faces of the border box (where the invisible barrier wall stands; the visible red edges are
     * part of it).
     */
    public boolean isShell(int x, int y, int z) {
        boolean insideX = x >= markerMinX() && x <= markerMaxX();
        boolean insideZ = z >= markerMinZ() && z <= markerMaxZ();
        boolean insideY = y >= bottomLimitY() && y <= markerTopY();
        if (!insideX || !insideZ || !insideY) {
            return false;
        }
        return x == markerMinX() || x == markerMaxX() || z == markerMinZ() || z == markerMaxZ()
                || y == bottomLimitY() || y == markerTopY();
    }

    /** Half width of the region an unlimited area may use (areas are SPACING apart, so they never meet). */
    public static final int UNLIMITED_HALF = SPACING / 2 - 100;
    public static final int WORLD_MIN_Y = -63;
    public static final int WORLD_MAX_Y = 318;

    /** Allowed space of the area as {min x, y, z, max x, y, z} (blocks, inclusive): the build box or the region. */
    public int[] limits(boolean unlimited) {
        if (unlimited) {
            return new int[] {centerX - UNLIMITED_HALF, WORLD_MIN_Y, centerZ - UNLIMITED_HALF,
                    centerX + UNLIMITED_HALF, WORLD_MAX_Y, centerZ + UNLIMITED_HALF};
        }
        return new int[] {buildMinX(), buildMinY(), buildMinZ(), buildMaxX(), buildMaxY(), buildMaxZ()};
    }

    /** Nearest point strictly inside the border box (for someone who got past the barrier, e.g. a spectator). */
    public double[] clampInside(double x, double y, double z) {
        return new double[] {
                Math.max(buildMinX() + 0.5, Math.min(buildMaxX() + 0.5, x)),
                Math.max(buildMinY(), Math.min(buildMaxY() - 1.0, y)),
                Math.max(buildMinZ() + 0.5, Math.min(buildMaxZ() + 0.5, z))};
    }

    /** True for a block position inside the area box (floor to ceiling layer, one extra ring for walls). */
    public boolean containsBlock(int x, int y, int z) {
        return x >= minX() - 1 && x <= maxX() + 1 && z >= minZ() - 1 && z <= maxZ() + 1
                && y >= floorY && y <= maxY();
    }
}
