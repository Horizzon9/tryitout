package com.ufuk.tryitout;

import com.mojang.logging.LogUtils;
import com.ufuk.tryitout.client.SandboxKeys;
import com.ufuk.tryitout.client.SandboxTrigger;
import com.ufuk.tryitout.config.SandboxConfig;
import dev.architectury.platform.Platform;
import com.ufuk.tryitout.screen.SandboxMenus;
import org.slf4j.Logger;

/** Client entry point shared by both loaders: the pocket scene. Registers no blocks, items or entities. */
public final class TryItOutClient {
    public static final String MOD_ID = "tryitout";
    public static final Logger LOGGER = LogUtils.getLogger();

    private TryItOutClient() {
    }

    public static void init() {
        SandboxConfig.load(Platform.getConfigFolder());
        SandboxKeys.init();
        SandboxTrigger.init();
        com.ufuk.tryitout.testworld.client.DummyRenderer.init();
        if (Platform.isModLoaded("ponder")) {
            com.ufuk.tryitout.compat.ponder.PonderButtons.init();
        }
        SandboxMenus.init();
        com.ufuk.tryitout.testworld.client.TestClient.init();
    }
}
