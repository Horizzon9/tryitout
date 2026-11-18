package com.ufuk.pocketsandbox.neoforge;

import com.ufuk.pocketsandbox.PocketSandboxClient;
import com.ufuk.pocketsandbox.platform.SandboxPlatform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Client entry point: the pocket scene. Never constructed on a dedicated server. */
@Mod(value = PocketSandboxClient.MOD_ID, dist = Dist.CLIENT)
public final class PocketSandboxNeoForgeClient {
    public PocketSandboxNeoForgeClient(IEventBus modBus, ModContainer container) {
        SandboxPlatform.installMesher(new NeoForgeBlockMesher());
        SandboxPlatform.installBlockEntityLoaded(be -> be.onLoad());
        PocketSandboxClient.init();
    }
}
