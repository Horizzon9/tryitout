package com.ufuk.tryitout.mixin;

import com.ufuk.tryitout.testworld.TestWorld;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nothing enters or leaves the test world through portals or other mods' teleports (items and mobs too). */
@Mixin(Entity.class)
public abstract class TestEntityRulesMixin {
    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void tryitout$testBorder(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        if (TestWorld.crossesTestBorder((Entity) (Object) this, transition.newLevel())) {
            cir.setReturnValue(null);
        }
    }
}
