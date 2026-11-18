package com.ufuk.pocketsandbox.client;

import com.ufuk.pocketsandbox.compat.ponder.PonderCompat;
import com.ufuk.pocketsandbox.screen.SandboxScreen;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientTooltipEvent;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Hold the sandbox key (W) over an item in any inventory, JEI or EMI screen: a bar fills in the tooltip, and
 * when it is full the pocket sandbox opens (Create's Ponder works the same way). Items that have a Ponder scene
 * are left to Ponder (its own W). Never runs while a text box has focus, inside our own screen, or outside a
 * world.
 */
public final class SandboxTrigger {
    /** Ticks the key must be held (0.75 s). */
    static final int HOLD_TICKS = 15;
    private static final int BAR_LENGTH = 24;

    private static ItemStack hovered = ItemStack.EMPTY;
    private static int held;
    private static int previousHeld;

    private SandboxTrigger() {
    }

    public static void init() {
        ClientTickEvent.CLIENT_POST.register(SandboxTrigger::tick);
        ClientTooltipEvent.ITEM.register((stack, lines, context, flag) -> tooltip(stack, lines));
    }

    private static boolean eligible(Minecraft mc, Screen screen) {
        return screen != null && !(screen instanceof SandboxScreen) && mc.level != null && mc.player != null
                && !PonderCompat.isPonderScreen(screen) && !HoveredItems.typing(screen);
    }

    private static void tick(Minecraft mc) {
        Screen screen = mc.screen;
        ItemStack now = eligible(mc, screen) ? HoveredItems.resolve(screen) : ItemStack.EMPTY;
        if (now.isEmpty() || PonderCompat.hasScene(now)) {
            reset(ItemStack.EMPTY);
            return;
        }
        if (!ItemStack.isSameItemSameComponents(now, hovered)) {
            reset(now.copyWithCount(1));
        }
        previousHeld = held;
        if (!SandboxKeys.held(SandboxKeys.OPEN)) {
            held = Math.max(0, held - 3);
            return;
        }
        held++;
        if (held >= HOLD_TICKS) {
            ItemStack target = hovered.copy();
            reset(ItemStack.EMPTY);
            var multiblock = com.ufuk.pocketsandbox.compat.ie.IeMultiblocks.capture(target);
            if (multiblock.isPresent()) {
                mc.setScreen(new SandboxScreen(screen, multiblock.get().stack(), multiblock.get().room()));
            } else {
                mc.setScreen(new SandboxScreen(screen, target));
            }
        }
    }

    private static void reset(ItemStack stack) {
        hovered = stack;
        held = 0;
        previousHeld = 0;
    }

    /** "Hold [W] for Pocket Sandbox", or the filling bar while the key is held. */
    private static void tooltip(ItemStack stack, List<Component> lines) {
        if (hovered.isEmpty() || !ItemStack.isSameItemSameComponents(stack, hovered)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        float progress = Math.min(1f, (previousHeld + (held - previousHeld) * partial) / HOLD_TICKS);
        Component key = SandboxKeys.OPEN.getTranslatedKeyMessage();
        if (progress <= 0f) {
            lines.add(Component.translatable("pocketsandbox.hold_to_open",
                    Component.literal("[").append(key).append("]").withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        int filled = Math.round(progress * BAR_LENGTH);
        MutableComponent bar = Component.literal("|".repeat(filled)).withStyle(ChatFormatting.GOLD);
        bar.append(Component.literal("|".repeat(BAR_LENGTH - filled)).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("pocketsandbox.hold_progress").withStyle(ChatFormatting.GRAY)
                .append(" ").append(bar));
    }
}
