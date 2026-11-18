package com.ufuk.tryitout.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ufuk.tryitout.testworld.client.TestClient;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The F3+F4 screen sends its "gamemode" command in the test world even without cheats. */
@Mixin(GameModeSwitcherScreen.class)
public abstract class TestGameModeSwitcherMixin {
    @WrapOperation(method = "switchToHoveredGameMode(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/screens/debug/GameModeSwitcherScreen$GameModeIcon;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasPermissions(I)Z"))
    private static boolean tryitout$testGameModes(LocalPlayer player, int level, Operation<Boolean> original) {
        return original.call(player, level) || TestClient.inTest();
    }
}
