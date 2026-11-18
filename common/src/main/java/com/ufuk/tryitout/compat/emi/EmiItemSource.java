package com.ufuk.tryitout.compat.emi;

import com.ufuk.tryitout.compat.ItemSources;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.minecraft.world.item.ItemStack;

/** Item under the mouse in the EMI panels (index, favorites, recipe screen). */
final class EmiItemSource implements ItemSources.Source {
    static final String NAME = "EMI";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ItemStack hoveredStack() {
        EmiStackInteraction hovered = EmiApi.getHoveredStack(false);
        if (hovered == null || hovered.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (EmiStack stack : hovered.getStack().getEmiStacks()) {
            ItemStack item = stack.getItemStack();
            if (!item.isEmpty()) {
                return item;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean searchFocused() {
        return EmiApi.isSearchFocused();
    }
}
