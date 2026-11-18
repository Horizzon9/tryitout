package com.ufuk.tryitout.mixin;

import com.ufuk.tryitout.screen.SandboxMenus;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Closing a sandbox GUI (Esc / E) goes back to the sandbox instead of telling the server. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "closeContainer", at = @At("HEAD"), cancellable = true)
    private void tryitout$backToSandbox(CallbackInfo ci) {
        if (SandboxMenus.closeToParent()) {
            ci.cancel();
        }
    }
}
