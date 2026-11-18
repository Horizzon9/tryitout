package com.ufuk.tryitout.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ufuk.tryitout.testworld.TestCrashGuard;
import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the test dimension a failing block entity or entity is removed instead of crashing the server. */
@Mixin(Level.class)
public abstract class TestLevelGuardMixin {
    @WrapOperation(method = "tickBlockEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void tryitout$guardBlockEntity(TickingBlockEntity ticking, Operation<Void> original) {
        Level self = (Level) (Object) this;
        if (!TestCrashGuard.guards(self)) {
            original.call(ticking);
            return;
        }
        try {
            original.call(ticking);
        } catch (Throwable error) {
            TestCrashGuard.blockEntityFailed(self, ticking.getPos(), error);
        }
    }

    @Inject(method = "guardEntityTick", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void tryitout$guardEntity(Consumer<T> tick, T entity, CallbackInfo ci) {
        if (!TestCrashGuard.guards((Level) (Object) this)) {
            return;
        }
        ci.cancel();
        try {
            tick.accept(entity);
        } catch (Throwable error) {
            TestCrashGuard.entityFailed(entity, error);
        }
    }
}
