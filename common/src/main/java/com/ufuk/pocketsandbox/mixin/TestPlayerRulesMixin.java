package com.ufuk.pocketsandbox.mixin;

import com.ufuk.pocketsandbox.testworld.TestWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Test world rules on the server player: no spawn point inside the test world (beds, anchors, any mod), no
 * statistics counted there, and no way into or out of it except our own teleport (portals, /tp, mods).
 */
@Mixin(ServerPlayer.class)
public abstract class TestPlayerRulesMixin {
    @Inject(method = "setRespawnPosition", at = @At("HEAD"), cancellable = true)
    private void pocketsandbox$noTestSpawn(ResourceKey<Level> dimension, BlockPos pos, float angle, boolean forced,
            boolean message, CallbackInfo ci) {
        if (dimension == TestWorld.DIMENSION) {
            ci.cancel();
        }
    }

    @Inject(method = "awardStat", at = @At("HEAD"), cancellable = true)
    private void pocketsandbox$noTestStats(Stat<?> stat, int amount, CallbackInfo ci) {
        if (TestWorld.isTestWorld(((ServerPlayer) (Object) this).level())) {
            ci.cancel();
        }
    }

    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void pocketsandbox$testBorder(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        if (TestWorld.crossesTestBorder((Entity) (Object) this, transition.newLevel())) {
            cir.setReturnValue(null);
        }
    }
}
