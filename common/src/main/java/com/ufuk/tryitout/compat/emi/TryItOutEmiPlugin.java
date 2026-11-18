package com.ufuk.tryitout.compat.emi;

import com.ufuk.tryitout.compat.ItemSources;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

/** Only EMI loads this class (NeoForge: {@code @EmiEntrypoint} scan, Fabric: {@code emi} entrypoint). Adds no recipes. */
@EmiEntrypoint
public class TryItOutEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        ItemSources.register(new EmiItemSource());
    }
}
