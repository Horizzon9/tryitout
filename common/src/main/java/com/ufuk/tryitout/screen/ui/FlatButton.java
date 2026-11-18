package com.ufuk.tryitout.screen.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A flat button in the TryItOut style. It is still a vanilla {@link Button} (same click, focus, narration and
 * keyboard behaviour), only drawn differently: dark rounded box, lighter on hover, blue when "active" (a toggled
 * tool), red for leaving / destructive actions, an optional item icon in front of the label.
 */
public class FlatButton extends Button {
    public enum Style { NORMAL, ACCENT, DANGER }

    private final Style style;
    @Nullable
    private final ItemStack icon;
    @Nullable
    private final BooleanSupplier highlighted;

    protected FlatButton(int x, int y, int w, int h, Component label, OnPress onPress, Style style,
            @Nullable ItemStack icon, @Nullable BooleanSupplier highlighted) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
        this.style = style;
        this.icon = icon;
        this.highlighted = highlighted;
    }

    public static Builder flat(Component label, OnPress onPress) {
        return new Builder(label, onPress);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hot = active && isHoveredOrFocused();
        boolean lit = highlighted != null && highlighted.getAsBoolean();
        int base = switch (style) {
            case ACCENT -> UiTheme.ACCENT_DARK;
            case DANGER -> UiTheme.DANGER_DARK;
            case NORMAL -> lit ? UiTheme.ACCENT_DARK : UiTheme.BUTTON;
        };
        int fill = !active ? UiTheme.mix(base, 0xFF000000, 0.35f) : hot ? UiTheme.mix(base, 0xFFFFFFFF, 0.12f) : base;
        int border = style == Style.ACCENT || lit ? UiTheme.ACCENT : style == Style.DANGER ? UiTheme.DANGER
                : hot ? UiTheme.MUTED : UiTheme.BUTTON_BORDER;
        UiTheme.box(graphics, x, y, w, h, fill);
        UiTheme.outline(graphics, x, y, w, h, border);
        Font font = Minecraft.getInstance().font;
        int textColor = active ? UiTheme.TEXT : UiTheme.DISABLED;
        int iconW = icon != null && !icon.isEmpty() && h >= 16 ? 16 : 0;
        Component label = getMessage();
        int room = w - 8 - (iconW > 0 ? iconW + 3 : 0);
        String text = label.getString();
        if (font.width(text) > room) {
            text = font.plainSubstrByWidth(text, Math.max(0, room - font.width("…"))) + "…";
            label = Component.literal(text);
        }
        int contentW = font.width(label) + (iconW > 0 ? iconW + 3 : 0);
        int cx = x + (w - contentW) / 2;
        if (iconW > 0) {
            graphics.renderItem(icon, cx, y + (h - 16) / 2);
            cx += iconW + 3;
        }
        graphics.drawString(font, label, cx, y + (h - 8) / 2, textColor, false);
    }

    /** Builder in the style of {@link Button#builder}. */
    public static final class Builder {
        private final Component label;
        private final OnPress onPress;
        private int x;
        private int y;
        private int w = 120;
        private int h = 18;
        private Style style = Style.NORMAL;
        @Nullable
        private ItemStack icon;
        @Nullable
        private BooleanSupplier highlighted;
        @Nullable
        private Tooltip tooltip;

        Builder(Component label, OnPress onPress) {
            this.label = label;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            return this;
        }

        public Builder style(Style style) {
            this.style = style;
            return this;
        }

        public Builder icon(ItemStack icon) {
            this.icon = icon;
            return this;
        }

        /** Drawn in the accent colour while this is true (a tool that is switched on). */
        public Builder highlighted(BooleanSupplier highlighted) {
            this.highlighted = highlighted;
            return this;
        }

        private boolean active = true;

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder tooltip(Component text) {
            this.tooltip = Tooltip.create(text);
            return this;
        }

        public FlatButton build() {
            FlatButton button = new FlatButton(x, y, w, h, label, onPress, style, icon, highlighted);
            button.active = active;
            if (tooltip != null) {
                button.setTooltip(tooltip);
            }
            return button;
        }
    }
}
