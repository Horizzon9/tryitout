package com.ufuk.tryitout.mixin;

import com.ufuk.tryitout.testworld.TestWorld;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A test player's body never breaks or drops anything; the damage goes to the real profile's health. */
@Mixin(ArmorStand.class)
public abstract class TestBodyMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void tryitout$bodyHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ArmorStand self = (ArmorStand) (Object) this;
        if (!self.level().isClientSide() && TestWorld.isBody(self)) {
            TestWorld.hurtBody(self, source, amount);
            cir.setReturnValue(false);
        }
    }
}
