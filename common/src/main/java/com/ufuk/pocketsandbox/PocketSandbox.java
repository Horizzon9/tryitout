package com.ufuk.pocketsandbox;

import com.mojang.logging.LogUtils;
import com.ufuk.pocketsandbox.testworld.TestNetwork;
import com.ufuk.pocketsandbox.testworld.TestServerConfig;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;

/**
 * Common entry point (client and dedicated server). Only the server-side test world (Faz B) lives here: its
 * config and network channel. The pocket scene itself stays client-only ({@link PocketSandboxClient}); when the
 * server lacks this mod the client simply never sees the test world channel.
 */
public final class PocketSandbox {
    public static final String MOD_ID = PocketSandboxClient.MOD_ID;
    public static final Logger LOGGER = LogUtils.getLogger();

    private PocketSandbox() {
    }

    public static void init() {
        TestServerConfig.load(Platform.getConfigFolder());
        TestNetwork.init();
    }
}
