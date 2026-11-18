package com.ufuk.pocketsandbox.screen.ui;

import com.ufuk.pocketsandbox.scene.WallSide;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A compass for the room's walls that turns with the player: "up" is always where the player looks, so the wall
 * in front of you is the letter at the top. Each letter is a button (green when that wall is up); the arrow in the
 * middle is you.
 */
public final class WallCompass extends AbstractWidget {
    private static final int DOT = 11;
    private final Predicate<WallSide> on;
    private final Consumer<WallSide> toggle;
    private WallSide hovered;

    public WallCompass(int x, int y, int size, Predicate<WallSide> on, Consumer<WallSide> toggle) {
        super(x, y, size, size, Component.translatable("pocketsandbox.menu.walls"));
        this.on = on;
        this.toggle = toggle;
    }

    /** World direction of a wall (x, z): north is -z, east +x, south +z, west -x. */
    private static double[] direction(WallSide side) {
        return switch (side) {
            case NORTH -> new double[] {0, -1};
            case EAST -> new double[] {1, 0};
            case SOUTH -> new double[] {0, 1};
            case WEST -> new double[] {-1, 0};
        };
    }

    /** Screen offset (unit circle) of a wall: the player's forward is up, their right is right. */
    private static double[] screen(WallSide side) {
        Minecraft mc = Minecraft.getInstance();
        float yaw = mc.player == null ? 180f : mc.player.getYRot();
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        double rx = -Math.cos(rad);
        double rz = -Math.sin(rad);
        double[] d = direction(side);
        // (rx, rz) is the player's right hand (facing south it is west, -x); screen y grows downwards.
        return new double[] {d[0] * rx + d[1] * rz, -(d[0] * fx + d[1] * fz)};
    }

    private int radius() {
        return getWidth() / 2 - DOT / 2 - 1;
    }

    private int[] centreOf(WallSide side) {
        double[] s = screen(side);
        int cx = getX() + getWidth() / 2;
        int cy = getY() + getHeight() / 2;
        return new int[] {cx + (int) Math.round(s[0] * radius()), cy + (int) Math.round(s[1] * radius())};
    }

    private WallSide sideAt(double mouseX, double mouseY) {
        WallSide best = null;
        double bestDistance = (DOT / 2.0 + 2) * (DOT / 2.0 + 2);
        for (WallSide side : WallSide.values()) {
            int[] c = centreOf(side);
            double dx = mouseX - c[0];
            double dy = mouseY - c[1];
            double distance = dx * dx + dy * dy;
            if (distance <= bestDistance) {
                best = side;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        WallSide side = sideAt(mouseX, mouseY);
        if (side != null) {
            toggle.accept(side);
        }
    }

    @Override
    protected boolean isValidClickButton(int button) {
        return button == 0;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int cx = getX() + getWidth() / 2;
        int cy = getY() + getHeight() / 2;
        int r = radius();
        // Ring: small dots around the circle.
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            int px = cx + (int) Math.round(Math.cos(a) * r);
            int py = cy + (int) Math.round(Math.sin(a) * r);
            graphics.fill(px, py, px + 1, py + 1, UiTheme.CARD_BORDER);
        }
        // You: an arrow pointing up (the way you look).
        graphics.drawString(font, "▲", cx - font.width("▲") / 2, cy - 4, UiTheme.ACCENT, false);
        hovered = isHovered() ? sideAt(mouseX, mouseY) : null;
        for (WallSide side : WallSide.values()) {
            int[] c = centreOf(side);
            boolean up = on.test(side);
            int x0 = c[0] - DOT / 2;
            int y0 = c[1] - DOT / 2;
            int fill = up ? UiTheme.ON : UiTheme.OFF;
            if (side == hovered) {
                fill = UiTheme.mix(fill, 0xFFFFFFFF, 0.25f);
            }
            UiTheme.box(graphics, x0, y0, DOT, DOT, fill);
            Component letter = Component.translatable("pocketsandbox.compass." + side.key());
            graphics.drawString(font, letter, c[0] - font.width(letter) / 2 + 1, c[1] - 3,
                    up ? 0xFF0E1A12 : UiTheme.TEXT, false);
        }
        if (hovered != null) {
            graphics.renderTooltip(font, Component.translatable("pocketsandbox.menu.wall." + hovered.key())
                    .append(": ").append(Component.translatable(on.test(hovered) ? "options.on" : "options.off")),
                    mouseX, mouseY);
        }
    }

    /** The wall straight ahead of the player (for the "you are facing" line). */
    public static WallSide facing() {
        WallSide best = WallSide.NORTH;
        double bestUp = Double.MAX_VALUE;
        for (WallSide side : WallSide.values()) {
            double up = screen(side)[1];
            if (up < bestUp) {
                bestUp = up;
                best = side;
            }
        }
        return best;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
