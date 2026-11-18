package com.ufuk.pocketsandbox.fabric;

import com.ufuk.pocketsandbox.PocketSandboxClient;
import net.fabricmc.api.ClientModInitializer;

public final class PocketSandboxFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PocketSandboxClient.init();
    }
}
