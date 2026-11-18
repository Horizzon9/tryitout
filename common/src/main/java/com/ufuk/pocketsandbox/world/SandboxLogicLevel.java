package com.ufuk.pocketsandbox.world;

import com.ufuk.pocketsandbox.safety.FaultRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.ticks.LevelTickAccess;

/**
 * "Server-side" facade of the same {@link SandboxWorld} ({@code isClientSide = false}). Used only for block
 * logic that vanilla and mods gate behind {@code !level.isClientSide}: placement ({@code setPlacedBy},
 * {@code onPlace}), use, breaking and neighbour updates. Server block-entity tickers never run here, and any
 * block entity created through this level is bound to the client {@link SandboxLevel} (rule 1).
 * {@code getServer()} is null and it is never a {@code ServerLevel}; code that needs one fails and is caught.
 */
public final class SandboxLogicLevel extends AbstractSandboxLevel {
    private final SandboxTicks<Block> blockTicks = new SandboxTicks<>();

    private SandboxLogicLevel(ClientLevel real, SandboxWorld world, FaultRegistry faults) {
        super(real, world, faults, false);
    }

    public static SandboxLogicLevel create(SandboxWorld world, FaultRegistry faults) {
        return new SandboxLogicLevel(requireLive(), world, faults);
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return blockTicks;
    }

    public SandboxTicks<Block> sandboxBlockTicks() {
        return blockTicks;
    }
}
