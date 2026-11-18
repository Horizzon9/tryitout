package com.ufuk.tryitout.neoforge;

import com.ufuk.tryitout.TryItOut;
import com.ufuk.tryitout.TryItOutClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Common entry point (client and dedicated server): the server-side test world channel. */
@Mod(TryItOutClient.MOD_ID)
public final class TryItOutNeoForge {
    public TryItOutNeoForge(IEventBus modBus, ModContainer container) {
        TryItOut.init();
    }
}
