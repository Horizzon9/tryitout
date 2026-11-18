package com.ufuk.tryitout.scene;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the floor is made of. Ponder (the default) is the white checkerboard of Create's Ponder scenes (snow block
 * and white concrete); stone is the grey checkerboard; the others let plants be placed: grass for
 * saplings and flowers, farmland for seeds, sand for cactus and dead bushes, end stone for chorus, soul sand for
 * nether wart, mycelium for mushrooms.
 */
public enum FloorType {
    PONDER, STONE, GRASS, FARMLAND, SAND, END_STONE, SOUL_SAND, MYCELIUM;

    /** Tried in this order when the hovered block cannot stand on the checkerboard floor. */
    static final FloorType[] FALLBACKS = {GRASS, FARMLAND, SAND, END_STONE, SOUL_SAND, MYCELIUM};

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public BlockState block(int x, int z) {
        return switch (this) {
            case PONDER -> ((x + z) & 1) == 0
                    ? Blocks.SNOW_BLOCK.defaultBlockState()
                    : Blocks.WHITE_CONCRETE.defaultBlockState();
            case STONE -> ((x + z) & 1) == 0
                    ? Blocks.SMOOTH_STONE.defaultBlockState()
                    : Blocks.POLISHED_ANDESITE.defaultBlockState();
            case GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case FARMLAND -> Blocks.FARMLAND.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7);
            case SAND -> Blocks.SAND.defaultBlockState();
            case END_STONE -> Blocks.END_STONE.defaultBlockState();
            case SOUL_SAND -> Blocks.SOUL_SAND.defaultBlockState();
            case MYCELIUM -> Blocks.MYCELIUM.defaultBlockState();
        };
    }
}
