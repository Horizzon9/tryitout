package com.ufuk.tryitout.world;

/**
 * The editable box of the scene: a square floor of {@code size} x {@code size} (5, 7, 9, 15 or 25) centred on x/z = 0,
 * the floor layer at y = 0 and room for blocks above it up to {@code height - 1}. Pure value; unit tested.
 */
public record SandboxBounds(int size, int height) {
    public static final int[] SIZES = {5, 7, 9, 15, 25, 51, 101};
    /** Big floors (51, 101) do not get walls as tall as they are wide: the room is at most this high. */
    public static final int MAX_HEIGHT = 33;
    public static final int DEFAULT_HEIGHT = 8;

    public SandboxBounds {
        if (size < 1 || size % 2 == 0) {
            throw new IllegalArgumentException("size must be odd and positive: " + size);
        }
        if (height < 2) {
            throw new IllegalArgumentException("height must be at least 2: " + height);
        }
    }

    /** Height fits a wall as tall as the floor is wide (floor layer + {@code size} blocks). */
    public static SandboxBounds ofSize(int size) {
        return new SandboxBounds(size, Math.max(DEFAULT_HEIGHT, Math.min(size + 1, MAX_HEIGHT)));
    }

    public int half() {
        return size / 2;
    }

    public int minX() {
        return -half();
    }

    public int maxX() {
        return half();
    }

    public int minZ() {
        return -half();
    }

    public int maxZ() {
        return half();
    }

    public int minY() {
        return 0;
    }

    public int maxY() {
        return height - 1;
    }

    /** True inside the box, floor layer included. */
    public boolean contains(int x, int y, int z) {
        return x >= minX() && x <= maxX() && y >= minY() && y <= maxY() && z >= minZ() && z <= maxZ();
    }

    /** True for positions the player may edit (above the floor layer). */
    public boolean editable(int x, int y, int z) {
        return contains(x, y, z) && y > minY();
    }

    /** The next size in {@link #SIZES}, wrapping around. */
    public SandboxBounds nextSize() {
        for (int i = 0; i < SIZES.length; i++) {
            if (SIZES[i] == size) {
                return ofSize(SIZES[(i + 1) % SIZES.length]);
            }
        }
        return ofSize(SIZES[0]);
    }

    /** Centre of the box (for the camera), y in the middle of the used height. */
    public double centerY() {
        return 1.5;
    }
}
