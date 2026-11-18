package com.ufuk.tryitout.client;

import com.ufuk.tryitout.TryItOutClient;
import com.ufuk.tryitout.compat.ItemSources;
import com.ufuk.tryitout.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Finds the item under the mouse (vanilla slot first, then JEI / EMI overlays). Ported from Mod Manifest. */
public final class HoveredItems {
    private HoveredItems() {
    }

    public static ItemStack resolve(Screen screen) {
        try {
            if (screen instanceof AbstractContainerScreen<?> container) {
                Slot slot = ((AbstractContainerScreenAccessor) container).tryitout$getHoveredSlot();
                if (slot != null && slot.hasItem()) {
                    return slot.getItem();
                }
            }
        } catch (RuntimeException e) {
            TryItOutClient.LOGGER.debug("Could not read the hovered slot", e);
        }
        return ItemSources.hoveredStack();
    }

    /** True while the player types into any text box: the key must never be taken then. */
    public static boolean typing(Screen screen) {
        if (isTextFocus(screen.getFocused())) {
            return true;
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox box && box.isFocused()) {
                return true;
            }
        }
        return ItemSources.searchFocused();
    }

    private static boolean isTextFocus(GuiEventListener focused) {
        if (focused instanceof EditBox box) {
            return box.isFocused();
        }
        return focused instanceof RecipeBookComponent book && book.isFocused();
    }
}
