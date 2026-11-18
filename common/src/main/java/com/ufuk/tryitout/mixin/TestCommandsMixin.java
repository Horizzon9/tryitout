package com.ufuk.tryitout.mixin;

import com.mojang.brigadier.ParseResults;
import com.ufuk.tryitout.testworld.TestSessions;
import com.ufuk.tryitout.testworld.TestWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Commands typed by a player in the test world: off by default, a short allow list when the room turns them on. */
@Mixin(Commands.class)
public abstract class TestCommandsMixin {
    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void tryitout$testCommands(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
        ServerPlayer player = parse.getContext().getSource().getPlayer();
        if (player == null || !TestWorld.isTestWorld(player.level())) {
            return;
        }
        if (TestSessions.quickGameMode(player, command)) {
            ci.cancel();
            return;
        }
        String refusal = TestSessions.commandRefusal(player, command);
        if (refusal != null) {
            player.sendSystemMessage(Component.translatable(refusal).withStyle(ChatFormatting.RED));
            ci.cancel();
        }
    }
}
