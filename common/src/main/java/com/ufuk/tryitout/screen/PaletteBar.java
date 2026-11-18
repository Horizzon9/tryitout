package com.ufuk.tryitout.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import com.ufuk.tryitout.screen.ui.UiTheme;
import net.minecraft.world.item.ItemStack;

/**
 * What the scene player holds: slot 1 is the hovered item, then a copy of the real hotbar (read only,
 * nothing is taken from the inventory). Keys 1-9 or a click select a slot.
 */
final class PaletteBar {
    static final int SLOT = 22;
    private static final int GAP = 2;
    private static final int MAX = 9;

    private final List<ItemStack> items = new ArrayList<>();
    private int selected;

    PaletteBar(ItemStack target) {
        items.add(target.copyWithCount(1));
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < Inventory.getSelectionSize() && items.size() < MAX; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && items.stream().noneMatch(s -> ItemStack.isSameItemSameComponents(s, stack))) {
                items.add(stack.copyWithCount(1));
            }
        }
    }

    /** Puts {@code stack} in the palette and selects it (already there: just selected; full: replaces the last slot). */
    void put(ItemStack stack) {
        for (int i = 0; i < items.size(); i++) {
            if (ItemStack.isSameItemSameComponents(items.get(i), stack)) {
                selected = i;
                return;
            }
        }
        if (items.size() < MAX) {
            items.add(stack.copyWithCount(1));
        } else {
            items.set(MAX - 1, stack.copyWithCount(1));
        }
        selected = items.size() - 1;
    }

    /** Right click: takes slot {@code index} out (slot 1, the scene's own item, always stays); false when nothing changed. */
    boolean remove(int index) {
        if (index <= 0 || index >= items.size()) {
            return false;
        }
        items.remove(index);
        if (selected > index || selected >= items.size()) {
            selected = Math.max(0, selected - 1);
        }
        return true;
    }

    /** Index of the "+" slot (opens the block picker). */
    int plusSlot() {
        return items.size();
    }

    ItemStack selected() {
        return items.get(selected);
    }

    /** Selects slot {@code index} (0-based); false when there is no such slot. */
    boolean select(int index) {
        if (index < 0 || index >= items.size()) {
            return false;
        }
        selected = index;
        return true;
    }

    int width() {
        return (items.size() + 1) * SLOT - GAP;
    }

    /** Slot under the mouse, or -1. */
    int slotAt(int left, int top, double mouseX, double mouseY) {
        if (mouseY < top || mouseY >= top + SLOT || mouseX < left) {
            return -1;
        }
        int index = (int) ((mouseX - left) / SLOT);
        return index <= items.size() ? index : -1;
    }

    void render(GuiGraphics graphics, Font font, int left, int top, double mouseX, double mouseY) {
        int size = SLOT - GAP;
        int hovered = slotAt(left, top, mouseX, mouseY);
        UiTheme.box(graphics, left - 3, top - 3, width() + 6, size + 6, UiTheme.PANEL);
        UiTheme.outline(graphics, left - 3, top - 3, width() + 6, size + 6, UiTheme.PANEL_BORDER);
        for (int i = 0; i <= items.size(); i++) {
            int x = left + i * SLOT;
            boolean plus = i == items.size();
            boolean picked = i == selected && !plus;
            UiTheme.box(graphics, x, top, size, size, i == hovered ? UiTheme.BUTTON_HOVER : UiTheme.BUTTON);
            UiTheme.outline(graphics, x, top, size, size, picked ? UiTheme.ACCENT : UiTheme.BUTTON_BORDER);
            if (picked) {
                UiTheme.outline(graphics, x + 1, top + 1, size - 2, size - 2, UiTheme.ACCENT_DARK);
            }
            if (plus) {
                graphics.drawCenteredString(font, "+", x + size / 2, top + (size - 8) / 2,
                        i == hovered ? UiTheme.TEXT : UiTheme.ACCENT);
                continue;
            }
            graphics.renderItem(items.get(i), x + (size - 16) / 2, top + (size - 16) / 2);
            // Small slot number in the corner, above the item.
            graphics.pose().pushPose();
            graphics.pose().translate(x + 2, top + 2, 250);
            graphics.pose().scale(0.5F, 0.5F, 1.0F);
            graphics.drawString(font, String.valueOf(i + 1), 0, 0, picked ? UiTheme.ACCENT : UiTheme.MUTED, false);
            graphics.pose().popPose();
        }
        if (hovered >= 0) {
            java.util.List<net.minecraft.network.chat.Component> lines = hovered == items.size()
                    ? java.util.List.of(net.minecraft.network.chat.Component.translatable("tryitout.palette.add"))
                    : hovered == 0 ? java.util.List.of(items.get(0).getHoverName())
                    : java.util.List.of(items.get(hovered).getHoverName(),
                            net.minecraft.network.chat.Component.translatable("tryitout.palette.remove")
                                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, lines, (int) mouseX, (int) mouseY);
        }
    }
}
