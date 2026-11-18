package com.ufuk.pocketsandbox.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ufuk.pocketsandbox.testworld.client.DummyReachRing;
import com.ufuk.pocketsandbox.testworld.client.DummyRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Training dummies (husks on the server) are drawn as Pocket Sandbox's wooden dummy on clients with the mod. */
@Mixin(EntityRenderDispatcher.class)
public abstract class DummyRenderMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void pocketsandbox$dummy(E entity, double x, double y, double z, float rotationYaw,
            float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (entity instanceof LivingEntity living && DummyReachRing.isDummy(entity)) {
            DummyRenderer.render(living, x, y, z, partialTick, pose, buffers, light);
            ci.cancel();
        }
    }
}
