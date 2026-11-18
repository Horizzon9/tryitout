package com.ufuk.tryitout.fabric;

import com.ufuk.tryitout.TryItOut;
import net.fabricmc.api.ModInitializer;

/** Common entry point (client and dedicated server): the server-side test world channel. */
public final class TryItOutFabricMain implements ModInitializer {
    @Override
    public void onInitialize() {
        TryItOut.init();
    }
}
