package com.ufuk.pocketsandbox.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A small drop-down panel of tick boxes under a toolbar button. Rows are either section titles or tick
 * boxes; several boxes may be ticked at once (a group of mutually exclusive choices just untick the others in
 * their action). Drawn on top of everything; a click outside closes it.
 */
final class DropdownMenu {
    private static final int ROW_H = 12;
    private static final int PAD = 4;
    private static final int BOX = 8;

    private record Row(Component label, @Nullable BooleanSupplier checked, @Nullable Runnable action) {
    }

    private final List<Row> rows = new ArrayList<>();
    private boolean open;
    private int left;
    private int top;
    private int width;

    DropdownMenu title(Component label) {
        rows.add(new Row(label, null, null));
        return this;
    }

    DropdownMenu option(Component label, BooleanSupplier checked, Runnable action) {
        rows.add(new Row(label, checked, action));
        return this;
    }

    boolean isBuilt() {
        return !rows.isEmpty();
    }

    boolean isOpen() {
        return open;
    }

    void toggle() {
        open = !open;
    }

    void close() {
        open = false;
    }

    /** Places the panel under an anchor whose right edge is {@code anchorRight}, kept on screen. */
    void layout(Font font, int anchorRight, int anchorBottom, int screenWidth) {
        int widest = 0;
        for (Row row : rows) {
            widest = Math.max(widest, font.width(row.label()) + (row.checked() == null ? 0 : BOX + 4));
        }
        width = widest + PAD * 2;
        left = Math.max(2, Math.min(anchorRight - width, screenWidth - width - 2));
        top = anchorBottom + 1;
    }

    private int height() {
        return rows.size() * ROW_H + PAD * 2 - 2;
    }

    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        com.ufuk.pocketsandbox.screen.ui.UiTheme.box(graphics, left, top, width, height(), 0xF4141820);
        com.ufuk.pocketsandbox.screen.ui.UiTheme.outline(graphics, left, top, width, height(), com.ufuk.pocketsandbox.screen.ui.UiTheme.PANEL_BORDER);
        int y = top + PAD;
        for (Row row : rows) {
            if (row.checked() == null) {
                graphics.drawString(font, row.label(), left + PAD, y + 1, com.ufuk.pocketsandbox.screen.ui.UiTheme.ACCENT);
            } else {
                boolean over = mouseX >= left && mouseX < left + width && mouseY >= y - 1 && mouseY < y + ROW_H - 1;
                if (over) {
                    graphics.fill(left + 1, y - 1, left + width - 1, y + ROW_H - 1, 0x40FFFFFF);
                }
                int bx = left + PAD;
                graphics.renderOutline(bx, y, BOX, BOX, 0xFFCCCCCC);
                if (row.checked().getAsBoolean()) {
                    graphics.fill(bx + 2, y + 2, bx + BOX - 2, y + BOX - 2, com.ufuk.pocketsandbox.screen.ui.UiTheme.ON);
                }
                graphics.drawString(font, row.label(), bx + BOX + 4, y, 0xFFFFFFFF);
            }
            y += ROW_H;
        }
        graphics.pose().popPose();
    }

    /** Handles a click while open: true when it was used (inside: an option; outside: closes the menu). */
    boolean mouseClicked(double mouseX, double mouseY) {
        if (!open) {
            return false;
        }
        if (mouseX < left || mouseX >= left + width || mouseY < top || mouseY >= top + height()) {
            open = false;
            return true;
        }
        int index = (int) ((mouseY - top - PAD + 1) / ROW_H);
        if (index >= 0 && index < rows.size() && rows.get(index).action() != null) {
            rows.get(index).action().run();
        }
        return true;
    }
}
