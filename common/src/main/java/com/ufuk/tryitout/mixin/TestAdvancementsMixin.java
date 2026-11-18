package com.ufuk.tryitout.mixin;

import com.ufuk.tryitout.testworld.TestWorld;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No advancements are earned inside the test world (they are not part of the profile that gets swapped back). */
@Mixin(PlayerAdvancements.class)
public abstract class TestAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private void tryitout$noTestAdvancements(AdvancementHolder advancement, String criterion,
            CallbackInfoReturnable<Boolean> cir) {
        if (player != null && TestWorld.isTestWorld(player.level())) {
            cir.setReturnValue(false);
        }
    }
}
