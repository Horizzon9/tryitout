package com.ufuk.tryitout.mixin;

import com.ufuk.tryitout.testworld.TestWorld;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The test level never writes the shared (overworld) time and never runs the vanilla weather cycle: its time
 * and weather belong to {@code TestClock}.
 */
@Mixin(ServerLevel.class)
public abstract class TestServerLevelMixin {
    @Inject(method = "setDayTime", at = @At("HEAD"), cancellable = true)
    private void tryitout$keepTestTime(long time, CallbackInfo ci) {
        if (TestWorld.isTestWorld((ServerLevel) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void tryitout$keepTestWeather(CallbackInfo ci) {
        if (TestWorld.isTestWorld((ServerLevel) (Object) this)) {
            ci.cancel();
        }
    }
}
