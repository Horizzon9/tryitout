package com.ufuk.pocketsandbox.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the slot under the mouse; vanilla keeps it protected. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("hoveredSlot")
    @Nullable
    Slot pocketsandbox$getHoveredSlot();

    @Accessor("leftPos")
    int pocketsandbox$getLeftPos();

    @Accessor("topPos")
    int pocketsandbox$getTopPos();
}
