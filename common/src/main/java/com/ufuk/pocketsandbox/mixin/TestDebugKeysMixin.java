package com.ufuk.pocketsandbox.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ufuk.pocketsandbox.testworld.client.TestClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * F3+F4 (game mode switcher) and F3+N work in the test world without cheats: the server lets "gamemode" through
 * for the player's own test character there (TestSessions.quickGameMode).
 */
@Mixin(KeyboardHandler.class)
public abstract class TestDebugKeysMixin {
    @WrapOperation(method = "handleDebugKeys", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;hasPermissions(I)Z"))
    private boolean pocketsandbox$testGameModes(LocalPlayer player, int level, Operation<Boolean> original) {
        return original.call(player, level) || TestClient.inTest();
    }
}
