package com.ufuk.pocketsandbox;

import com.mojang.logging.LogUtils;
import com.ufuk.pocketsandbox.client.SandboxKeys;
import com.ufuk.pocketsandbox.client.SandboxTrigger;
import com.ufuk.pocketsandbox.config.SandboxConfig;
import dev.architectury.platform.Platform;
import com.ufuk.pocketsandbox.screen.SandboxMenus;
import org.slf4j.Logger;

/** Client entry point shared by both loaders: the pocket scene. Registers no blocks, items or entities. */
public final class PocketSandboxClient {
    public static final String MOD_ID = "pocketsandbox";
    public static final Logger LOGGER = LogUtils.getLogger();

    private PocketSandboxClient() {
    }

    public static void init() {
        SandboxConfig.load(Platform.getConfigFolder());
        SandboxKeys.init();
        SandboxTrigger.init();
        com.ufuk.pocketsandbox.testworld.client.DummyRenderer.init();
        if (Platform.isModLoaded("ponder")) {
            com.ufuk.pocketsandbox.compat.ponder.PonderButtons.init();
        }
        SandboxMenus.init();
        com.ufuk.pocketsandbox.testworld.client.TestClient.init();
    }
}
