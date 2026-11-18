package com.ufuk.pocketsandbox.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ufuk.pocketsandbox.PocketSandbox;
import com.ufuk.pocketsandbox.testworld.TestWorld;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While the player is in the test world, a block entity or entity renderer that throws skips that one thing
 * instead of crashing the game; the pose stack is put back to its depth before the call.
 */
@Mixin(LevelRenderer.class)
public abstract class TestRenderGuardMixin {
    @Unique
    private static final Set<String> POCKETSANDBOX_REPORTED = new HashSet<>();

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private void pocketsandbox$guardBlockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntity be, float partialTick,
            PoseStack pose, MultiBufferSource buffers, Operation<Void> original) {
        if (!TestWorld.isTestWorld(Minecraft.getInstance().level)) {
            original.call(dispatcher, be, partialTick, pose, buffers);
            return;
        }
        int depth = ((PoseStackAccessor) pose).pocketsandbox$stack().size();
        try {
            original.call(dispatcher, be, partialTick, pose, buffers);
        } catch (Throwable error) {
            pocketsandbox$restore(pose, depth);
            pocketsandbox$report("block entity " + be.getType(), error);
        }
    }

    @WrapOperation(method = "renderEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void pocketsandbox$guardEntity(EntityRenderDispatcher dispatcher, Entity entity, double x, double y, double z,
            float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original) {
        if (!TestWorld.isTestWorld(entity.level())) {
            original.call(dispatcher, entity, x, y, z, yaw, partialTick, pose, buffers, light);
            return;
        }
        int depth = ((PoseStackAccessor) pose).pocketsandbox$stack().size();
        try {
            original.call(dispatcher, entity, x, y, z, yaw, partialTick, pose, buffers, light);
        } catch (Throwable error) {
            pocketsandbox$restore(pose, depth);
            pocketsandbox$report("entity " + entity.getType(), error);
        }
    }

    @Unique
    private static void pocketsandbox$restore(PoseStack pose, int depth) {
        while (((PoseStackAccessor) pose).pocketsandbox$stack().size() > depth) {
            pose.popPose();
        }
    }

    @Unique
    private static void pocketsandbox$report(String what, Throwable error) {
        if (POCKETSANDBOX_REPORTED.add(what)) {
            PocketSandbox.LOGGER.error("Pocket Sandbox: rendering {} in the test world failed; it is skipped", what, error);
        }
    }
}
