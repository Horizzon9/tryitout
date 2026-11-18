package com.ufuk.tryitout.mixin;

import com.ufuk.tryitout.screen.SandboxMenus;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clicks in a sandbox GUI run on the local scene menu and never become packets. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
    private void tryitout$localClick(int containerId, int slotId, int button, ClickType type, Player player,
            CallbackInfo ci) {
        if (SandboxMenus.click(containerId, slotId, button, type)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleInventoryButtonClick", at = @At("HEAD"), cancellable = true)
    private void tryitout$localButton(int containerId, int buttonId, CallbackInfo ci) {
        if (SandboxMenus.button(containerId, buttonId)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void tryitout$noRecipePlacement(int containerId, RecipeHolder<?> recipe, boolean shift,
            CallbackInfo ci) {
        if (SandboxMenus.placeRecipe(containerId)) {
            ci.cancel();
        }
    }
}
