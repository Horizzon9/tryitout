package com.ufuk.pocketsandbox.world;

import java.util.function.BooleanSupplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jetbrains.annotations.Nullable;

/** Chunk source of the scene: hands out the scene's delegating chunks, never loads or ticks anything. */
final class SandboxChunkSource extends ChunkSource {
    private final AbstractSandboxLevel level;

    SandboxChunkSource(AbstractSandboxLevel level) {
        this.level = level;
    }

    @Override
    @Nullable
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean load) {
        return level.getChunk(chunkX, chunkZ);
    }

    @Override
    public void tick(BooleanSupplier hasTimeLeft, boolean tickChunks) {
    }

    @Override
    public String gatherStats() {
        return "Pocket Sandbox";
    }

    @Override
    public int getLoadedChunksCount() {
        return 1;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return level.getLightEngine();
    }

    @Override
    public BlockGetter getLevel() {
        return level;
    }
}
