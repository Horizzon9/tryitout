package com.ufuk.pocketsandbox.neoforge;

import com.ufuk.pocketsandbox.PocketSandbox;
import com.ufuk.pocketsandbox.PocketSandboxClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Common entry point (client and dedicated server): the server-side test world channel. */
@Mod(PocketSandboxClient.MOD_ID)
public final class PocketSandboxNeoForge {
    public PocketSandboxNeoForge(IEventBus modBus, ModContainer container) {
        PocketSandbox.init();
    }
}
