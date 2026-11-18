package com.ufuk.tryitout.testworld;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Invitations into someone's test world. The invited player gets a clickable chat line that runs
 * {@code /tryitout accept <host>}; an invitation lasts one minute. Accepting swaps the guest's own profile
 * exactly like entering on their own (the guest gets a fresh test character). Only the owner changes settings
 * or saves; when the owner leaves, every guest is sent back with their real profile.
 */
final class TestInvites {
    private static final int EXPIRE_TICKS = 20 * 60;
    /** Invited player → (host, tick the invitation was sent). */
    private static final Map<UUID, Object[]> pending = new HashMap<>();

    private TestInvites() {
    }

    static void invite(ServerPlayer host, ServerPlayer target) {
        pending.put(target.getUUID(), new Object[] {host.getUUID(), host.server.getTickCount()});
        String name = host.getGameProfile().getName();
        Component accept = Component.translatable("tryitout.test.invite.accept").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tryitout accept " + name))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("tryitout.test.invite.accept_hover"))));
        target.sendSystemMessage(Component.translatable("tryitout.test.invite.received", name)
                .withStyle(ChatFormatting.GOLD).append(" ").append(accept));
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tryitout")
                .then(Commands.literal("accept")
                        .then(Commands.argument("host", StringArgumentType.word())
                                .executes(context -> accept(context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "host")))))
                .then(Commands.literal("decline")
                        .executes(context -> {
                            pending.remove(context.getSource().getPlayerOrException().getUUID());
                            return 1;
                        })));
    }

    private static int accept(ServerPlayer guest, String hostName) {
        Object[] invite = pending.remove(guest.getUUID());
        ServerPlayer host = guest.server.getPlayerList().getPlayerByName(hostName);
        if (invite == null || host == null || !host.getUUID().equals(invite[0])
                || guest.server.getTickCount() - (Integer) invite[1] > EXPIRE_TICKS) {
            guest.sendSystemMessage(Component.translatable("tryitout.test.invite.gone").withStyle(ChatFormatting.RED));
            return 0;
        }
        TestSessions.enterAsGuest(guest, host);
        return 1;
    }

    static void clear() {
        pending.clear();
    }
}
