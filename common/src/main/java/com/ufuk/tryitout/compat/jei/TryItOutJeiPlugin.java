package com.ufuk.tryitout.compat.jei;

import com.ufuk.tryitout.TryItOutClient;
import com.ufuk.tryitout.compat.ItemSources;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/** Only JEI loads this class (NeoForge: {@code @JeiPlugin} scan, Fabric: {@code jei_mod_plugin} entrypoint). */
@JeiPlugin
public class TryItOutJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(TryItOutClient.MOD_ID, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        ItemSources.register(new JeiItemSource(runtime));
    }

    @Override
    public void onRuntimeUnavailable() {
        ItemSources.unregister(JeiItemSource.NAME);
    }
}
