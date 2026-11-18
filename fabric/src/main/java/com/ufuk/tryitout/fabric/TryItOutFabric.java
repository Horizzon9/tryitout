package com.ufuk.tryitout.fabric;

import com.ufuk.tryitout.TryItOutClient;
import net.fabricmc.api.ClientModInitializer;

public final class TryItOutFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TryItOutClient.init();
    }
}
