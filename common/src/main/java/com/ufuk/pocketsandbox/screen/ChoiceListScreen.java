package com.ufuk.pocketsandbox.screen;

import com.ufuk.pocketsandbox.screen.ui.UiTheme;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A drop-down list opened from a settings line: the screen behind stays visible, the options open right under the
 * line (or above it when there is no room), the current one is marked. A click picks; a click elsewhere or Esc
 * closes it.
 */
public final class ChoiceListScreen extends Screen {
    private static final int ROW = 14;
    @Nullable
    private final Screen parent;
    private final List<Component> options;
    private final int selected;
    private final IntConsumer onPick;
    private final int anchorX;
    private final int anchorTop;
    private final int anchorBottom;
    private final int listW;
    private int x;
    private int y;

    public ChoiceListScreen(@Nullable Screen parent, int anchorX, int anchorTop, int anchorBottom, int width,
            List<Component> options, int selected, IntConsumer onPick) {
        super(Component.empty());
        this.parent = parent;
        this.options = options;
        this.selected = selected;
        this.onPick = onPick;
        this.anchorX = anchorX;
        this.anchorTop = anchorTop;
        this.anchorBottom = anchorBottom;
        this.listW = width;
    }

    @Override
    protected void init() {
        if (parent != null) {
            parent.init(minecraft, width, height); // keep the screen behind laid out for drawing
        }
        int h = options.size() * ROW + 4;
        x = Math.max(4, Math.min(anchorX, width - listW - 4));
        y = anchorBottom + h <= height - 4 ? anchorBottom : Math.max(4, anchorTop - h);
    }

    private int indexAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + listW || mouseY < y + 2) {
            return -1;
        }
        int i = (int) ((mouseY - y - 2) / ROW);
        return i < options.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int i = indexAt(mouseX, mouseY);
        if (i >= 0) {
            onPick.accept(i);
        }
        onClose();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (parent != null) {
            parent.render(graphics, -1, -1, partialTick);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        int h = options.size() * ROW + 4;
        UiTheme.box(graphics, x, y, listW, h, 0xF8141820);
        UiTheme.outline(graphics, x, y, listW, h, UiTheme.ACCENT);
        int hovered = indexAt(mouseX, mouseY);
        for (int i = 0; i < options.size(); i++) {
            int rowY = y + 2 + i * ROW;
            if (i == hovered) {
                graphics.fill(x + 1, rowY, x + listW - 1, rowY + ROW, 0x40FFFFFF);
            }
            if (i == selected) {
                graphics.drawString(font, "✔", x + 5, rowY + 3, UiTheme.ON, false);
            }
            graphics.drawString(font, options.get(i), x + 16, rowY + 3, i == selected ? UiTheme.ACCENT : UiTheme.TEXT,
                    false);
        }
        graphics.pose().popPose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
