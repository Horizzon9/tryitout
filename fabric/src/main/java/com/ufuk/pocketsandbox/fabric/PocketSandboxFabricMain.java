package com.ufuk.pocketsandbox.fabric;

import com.ufuk.pocketsandbox.PocketSandbox;
import net.fabricmc.api.ModInitializer;

/** Common entry point (client and dedicated server): the server-side test world channel. */
public final class PocketSandboxFabricMain implements ModInitializer {
    @Override
    public void onInitialize() {
        PocketSandbox.init();
    }
}
