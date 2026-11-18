package com.ufuk.tryitout;

import com.mojang.logging.LogUtils;
import com.ufuk.tryitout.testworld.TestNetwork;
import com.ufuk.tryitout.testworld.TestServerConfig;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;

/**
 * Common entry point (client and dedicated server). Only the server-side test world (Faz B) lives here: its
 * config and network channel. The pocket scene itself stays client-only ({@link TryItOutClient}); when the
 * server lacks this mod the client simply never sees the test world channel.
 */
public final class TryItOut {
    public static final String MOD_ID = TryItOutClient.MOD_ID;
    public static final Logger LOGGER = LogUtils.getLogger();

    private TryItOut() {
    }

    public static void init() {
        TestServerConfig.load(Platform.getConfigFolder());
        TestNetwork.init();
    }
}
