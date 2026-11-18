package com.ufuk.pocketsandbox.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Chunk view for mods that go through {@code level.getChunk(pos)} instead of the level. It stores nothing:
 * every read and write goes straight to the scene level / {@link SandboxWorld}.
 */
final class SandboxChunk extends EmptyLevelChunk {
    private final AbstractSandboxLevel sandbox;

    SandboxChunk(AbstractSandboxLevel level, ChunkPos pos, Holder<Biome> biome) {
        super(level, pos, biome);
        this.sandbox = level;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return sandbox.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return sandbox.getFluidState(pos);
    }

    @Override
    public FluidState getFluidState(int x, int y, int z) {
        return sandbox.getFluidState(new BlockPos(x, y, z));
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        return sandbox.getBlockEntity(pos);
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos, EntityCreationType creationType) {
        return sandbox.getBlockEntity(pos);
    }

    @Override
    @Nullable
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean isMoving) {
        BlockState old = sandbox.getBlockState(pos);
        return sandbox.setBlock(pos, state, 3) ? old : null;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
