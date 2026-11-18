package com.ufuk.tryitout.world.light;

import java.util.ArrayDeque;

/**
 * Block and sky light of a small box, computed from scratch with a flood fill (the box is at most
 * 11 x 9 x 11 cells, so a full recompute costs well under a millisecond). Pure: no Minecraft classes.
 *
 * <p>Rules follow vanilla closely: light drops by {@code max(1, opacity)} of the cell it enters; sky light
 * falls straight down through fully transparent cells without loss; cells outside the box are open air.
 */
public final class LightGrid {
    public static final int MAX = 15;
    private static final int[][] DIRECTIONS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int[] block;
    private final int[] sky;

    private LightGrid(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.block = new int[sizeX * sizeY * sizeZ];
        this.sky = new int[sizeX * sizeY * sizeZ];
    }

    /**
     * @param emission light emitted per cell (0..15), index {@code (y * sizeZ + z) * sizeX + x}
     * @param opacity light blocked per cell (0..15), same indexing
     */
    public static LightGrid compute(int sizeX, int sizeY, int sizeZ, int[] emission, int[] opacity) {
        int cells = sizeX * sizeY * sizeZ;
        if (emission.length != cells || opacity.length != cells) {
            throw new IllegalArgumentException("arrays must have " + cells + " cells");
        }
        LightGrid grid = new LightGrid(sizeX, sizeY, sizeZ);
        grid.fillBlock(emission, opacity);
        grid.fillSky(opacity);
        return grid;
    }

    public int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    public boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    public int block(int x, int y, int z) {
        return inside(x, y, z) ? block[index(x, y, z)] : 0;
    }

    /** Sky light; above and around the box it is open sky (15). */
    public int sky(int x, int y, int z) {
        return inside(x, y, z) ? sky[index(x, y, z)] : MAX;
    }

    private void fillBlock(int[] emission, int[] opacity) {
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int value = clamp(emission[index(x, y, z)]);
                    if (value > 0) {
                        block[index(x, y, z)] = value;
                        queue.add(new int[] {x, y, z});
                    }
                }
            }
        }
        spread(queue, block, opacity);
    }

    private void fillSky(int[] opacity) {
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int level = MAX;
                for (int y = sizeY - 1; y >= 0; y--) {
                    int cellOpacity = clamp(opacity[index(x, y, z)]);
                    level = cellOpacity == 0 ? level : Math.max(0, level - cellOpacity);
                    sky[index(x, y, z)] = level;
                    if (level > 0) {
                        queue.add(new int[] {x, y, z});
                    }
                }
            }
        }
        // Open sky around the box lights its sides too.
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    if (x == 0 || z == 0 || x == sizeX - 1 || z == sizeZ - 1) {
                        int i = index(x, y, z);
                        int fromOutside = Math.max(0, MAX - Math.max(1, clamp(opacity[i])));
                        if (fromOutside > sky[i]) {
                            sky[i] = fromOutside;
                            queue.add(new int[] {x, y, z});
                        }
                    }
                }
            }
        }
        spread(queue, sky, opacity);
    }

    private void spread(ArrayDeque<int[]> queue, int[] light, int[] opacity) {
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int current = light[index(cell[0], cell[1], cell[2])];
            for (int[] d : DIRECTIONS) {
                int nx = cell[0] + d[0];
                int ny = cell[1] + d[1];
                int nz = cell[2] + d[2];
                if (!inside(nx, ny, nz)) {
                    continue;
                }
                int ni = index(nx, ny, nz);
                int next = current - Math.max(1, clamp(opacity[ni]));
                if (next > light[ni]) {
                    light[ni] = next;
                    queue.add(new int[] {nx, ny, nz});
                }
            }
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX, value));
    }
}
