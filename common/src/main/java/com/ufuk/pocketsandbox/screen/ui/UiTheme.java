package com.ufuk.pocketsandbox.screen.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Colours and small drawing helpers shared by Pocket Sandbox screens: flat dark panels, soft rounded corners,
 * one blue accent, green for "on", red for leaving / destructive actions.
 */
public final class UiTheme {
    public static final int PANEL = 0xE8141820;
    public static final int PANEL_BORDER = 0xFF2E3846;
    public static final int CARD = 0xC01C222C;
    public static final int CARD_BORDER = 0xFF2A323E;
    public static final int BUTTON = 0xFF262E3A;
    public static final int BUTTON_HOVER = 0xFF323C4B;
    public static final int BUTTON_BORDER = 0xFF3A4555;
    public static final int ACCENT = 0xFF4FA3FF;
    public static final int ACCENT_DARK = 0xFF2B6CB8;
    public static final int ON = 0xFF3DCB7A;
    public static final int OFF = 0xFF4A5363;
    public static final int DANGER = 0xFFD9534F;
    public static final int DANGER_DARK = 0xFF8E2E2B;
    public static final int TEXT = 0xFFE8ECF1;
    public static final int MUTED = 0xFF8C96A4;
    public static final int DISABLED = 0xFF5C6572;

    private UiTheme() {
    }

    /** Filled rectangle with the four corner pixels left out (a soft rounded look at GUI scale). */
    public static void box(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        if (w < 3 || h < 3) {
            graphics.fill(x, y, x + w, y + h, color);
            return;
        }
        graphics.fill(x + 1, y, x + w - 1, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** One-pixel rounded outline. */
    public static void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x + 1, y, x + w - 1, y + 1, color);
        graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Panel background with border. */
    public static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        box(graphics, x, y, w, h, PANEL);
        outline(graphics, x, y, w, h, PANEL_BORDER);
    }

    /** Card (a group inside a panel) with a title in the accent colour and a thin line under it. */
    public static void card(GuiGraphics graphics, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component
            title, int x, int y, int w, int h) {
        box(graphics, x, y, w, h, CARD);
        outline(graphics, x, y, w, h, CARD_BORDER);
        graphics.drawString(font, title, x + 6, y + 5, ACCENT, false);
        graphics.fill(x + 6, y + 15, x + w - 6, y + 16, CARD_BORDER);
    }

    /** Colour halfway between {@code a} and {@code b} (alpha included). */
    public static int mix(int a, int b, float t) {
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }
}
