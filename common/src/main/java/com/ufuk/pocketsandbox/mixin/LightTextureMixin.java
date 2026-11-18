package com.ufuk.pocketsandbox.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ufuk.pocketsandbox.render.SandboxLightmap;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the sandbox scene renders, render types that bind "the" light map get the scene's own instead.
 * Outside the scene ({@code activeId() == -1}) vanilla behaviour is untouched.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @Inject(method = "turnOnLightLayer", at = @At("HEAD"), cancellable = true)
    private void pocketsandbox$useSceneLightmap(CallbackInfo ci) {
        int id = SandboxLightmap.activeId();
        if (id >= 0) {
            RenderSystem.setShaderTexture(2, id);
            ci.cancel();
        }
    }
}
