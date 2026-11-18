package com.ufuk.tryitout.world.light;

import com.ufuk.tryitout.safety.FaultRegistry;
import com.ufuk.tryitout.safety.Safe;
import com.ufuk.tryitout.world.SandboxBounds;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The scene's light map: a {@link LightGrid} over the box plus a one-block air border and one layer of sky
 * above it, recomputed lazily whenever the world's version changes. Emission and opacity come from the
 * blocks themselves (modded overrides included), each call guarded.
 */
public final class SceneLighting {
    private static final int BORDER = 1;

    private LightGrid grid;
    private int builtVersion = -1;
    private int originX;
    private int originY;
    private int originZ;
    private boolean computing;

    /** Recomputes when {@code version} differs from the last build. */
    public void ensure(int version, SandboxBounds bounds, BlockGetter level, Function<BlockPos, BlockState> states,
            FaultRegistry faults) {
        if (computing || (version == builtVersion && grid != null)) {
            return; // a block asking for light while we compute gets the previous grid
        }
        computing = true;
        try {
            rebuild(version, bounds, level, states, faults);
        } finally {
            computing = false;
        }
    }

    private void rebuild(int version, SandboxBounds bounds, BlockGetter level, Function<BlockPos, BlockState> states,
            FaultRegistry faults) {
        builtVersion = version;
        originX = bounds.minX() - BORDER;
        originY = bounds.minY();
        originZ = bounds.minZ() - BORDER;
        int sizeX = bounds.size() + 2 * BORDER;
        int sizeZ = bounds.size() + 2 * BORDER;
        int sizeY = bounds.height() + 1;
        int[] emission = new int[sizeX * sizeY * sizeZ];
        int[] opacity = new int[sizeX * sizeY * sizeZ];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    pos.set(originX + x, originY + y, originZ + z);
                    BlockState state = states.apply(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    int i = (y * sizeZ + z) * sizeX + x;
                    BlockPos at = pos.immutable();
                    String key = "light@" + at.getX() + "," + at.getY() + "," + at.getZ();
                    emission[i] = Safe.get(faults, key, () -> state.getLightEmission(), 0);
                    opacity[i] = Safe.get(faults, key, () -> state.getLightBlock(level, at), 15);
                }
            }
        }
        grid = LightGrid.compute(sizeX, sizeY, sizeZ, emission, opacity);
    }

    public int block(BlockPos pos) {
        return grid == null ? 0 : grid.block(pos.getX() - originX, pos.getY() - originY, pos.getZ() - originZ);
    }

    public int sky(BlockPos pos) {
        if (grid == null) {
            return LightGrid.MAX;
        }
        return grid.sky(pos.getX() - originX, pos.getY() - originY, pos.getZ() - originZ);
    }
}
