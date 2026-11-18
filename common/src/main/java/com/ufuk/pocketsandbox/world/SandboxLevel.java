package com.ufuk.pocketsandbox.world;

import com.ufuk.pocketsandbox.safety.FaultRegistry;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Client-side facade of the scene ({@code isClientSide = true}): what renderers, {@code animateTick} and
 * client block-entity tickers see. Every block entity of the scene belongs to this level (rule 1).
 */
public final class SandboxLevel extends AbstractSandboxLevel {
    private SandboxLevel(ClientLevel real, SandboxWorld world, FaultRegistry faults) {
        super(real, world, faults, true);
        world.attach(this);
    }

    /** Creates the scene level; only while a real client level exists. */
    public static SandboxLevel create(SandboxWorld world, FaultRegistry faults) {
        return new SandboxLevel(requireLive(), world, faults);
    }
}
