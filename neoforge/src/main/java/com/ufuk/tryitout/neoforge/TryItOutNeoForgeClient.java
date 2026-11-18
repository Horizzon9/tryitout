package com.ufuk.tryitout.neoforge;

import com.ufuk.tryitout.TryItOutClient;
import com.ufuk.tryitout.platform.SandboxPlatform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Client entry point: the pocket scene. Never constructed on a dedicated server. */
@Mod(value = TryItOutClient.MOD_ID, dist = Dist.CLIENT)
public final class TryItOutNeoForgeClient {
    public TryItOutNeoForgeClient(IEventBus modBus, ModContainer container) {
        SandboxPlatform.installMesher(new NeoForgeBlockMesher());
        SandboxPlatform.installBlockEntityLoaded(be -> be.onLoad());
        TryItOutClient.init();
    }
}
