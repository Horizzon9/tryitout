package com.ufuk.pocketsandbox.compat.jei;

import com.ufuk.pocketsandbox.compat.ItemSources;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;

/** Item under the mouse in the JEI ingredient list, bookmarks or recipe screen. */
final class JeiItemSource implements ItemSources.Source {
    static final String NAME = "JEI";
    private final IJeiRuntime runtime;

    JeiItemSource(IJeiRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ItemStack hoveredStack() {
        ItemStack stack = runtime.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (stack != null && !stack.isEmpty()) {
            return stack;
        }
        stack = runtime.getBookmarkOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (stack != null && !stack.isEmpty()) {
            return stack;
        }
        return runtime.getRecipesGui().getIngredientUnderMouse(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
    }

    @Override
    public boolean searchFocused() {
        return runtime.getIngredientListOverlay().hasKeyboardFocus();
    }
}
