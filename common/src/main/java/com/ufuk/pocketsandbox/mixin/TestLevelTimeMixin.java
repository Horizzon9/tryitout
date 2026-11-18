package com.ufuk.pocketsandbox.mixin;

import com.ufuk.pocketsandbox.testworld.TestClock;
import com.ufuk.pocketsandbox.testworld.TestWorld;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The server-side test level reads its day time from {@link TestClock} and its game rules from {@code TestRules}. */
@Mixin(Level.class)
public abstract class TestLevelTimeMixin {
    @Inject(method = "getGameRules", at = @At("HEAD"), cancellable = true)
    private void pocketsandbox$testRules(CallbackInfoReturnable<net.minecraft.world.level.GameRules> cir) {
        Level self = (Level) (Object) this;
        if (!self.isClientSide() && com.ufuk.pocketsandbox.testworld.TestRules.active() && TestWorld.isTestWorld(self)
                && self.getServer() != null) {
            cir.setReturnValue(com.ufuk.pocketsandbox.testworld.TestRules.rules(self.getServer()));
        }
    }

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void pocketsandbox$testDayTime(CallbackInfoReturnable<Long> cir) {
        Level self = (Level) (Object) this;
        if (!self.isClientSide() && TestClock.active() && TestWorld.isTestWorld(self)) {
            cir.setReturnValue(TestClock.dayTime());
        }
    }
}
