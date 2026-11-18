package com.ufuk.pocketsandbox.screen;

import com.ufuk.pocketsandbox.screen.ui.FlatButton;
import com.ufuk.pocketsandbox.screen.ui.UiTheme;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** A small "type a value" window (dummy name, dummy health ...): Enter or OK applies, Esc / Cancel goes back. */
public final class TextPromptScreen extends Screen {
    private static final int W = 200;
    @Nullable
    private final Screen parent;
    private final Component hint;
    private final String initial;
    private final int maxLength;
    private final Predicate<String> filter;
    private final Consumer<String> onDone;
    private EditBox box;
    private int panelY;

    public TextPromptScreen(@Nullable Screen parent, Component title, Component hint, String initial, int maxLength,
            Predicate<String> filter, Consumer<String> onDone) {
        super(title);
        this.parent = parent;
        this.hint = hint;
        this.initial = initial;
        this.maxLength = maxLength;
        this.filter = filter;
        this.onDone = onDone;
    }

    /** Only digits (for numbers). */
    public static Predicate<String> digits() {
        return text -> text.chars().allMatch(Character::isDigit);
    }

    @Override
    protected void init() {
        int x = width / 2 - W / 2;
        panelY = height / 2 - 48;
        box = new EditBox(font, x, panelY + 40, W, 20, title);
        box.setMaxLength(maxLength);
        box.setFilter(filter);
        box.setValue(initial);
        addRenderableWidget(box);
        setInitialFocus(box);
        int bw = (W - 4) / 2;
        addRenderableWidget(FlatButton.flat(CommonComponents.GUI_DONE, b -> done())
                .bounds(x, panelY + 66, bw, 20).style(FlatButton.Style.ACCENT).build());
        addRenderableWidget(FlatButton.flat(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(x + bw + 4, panelY + 66, bw, 20).build());
    }

    private void done() {
        onDone.accept(box.getValue());
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            done();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiTheme.panel(graphics, width / 2 - W / 2 - 8, panelY, W + 16, 96);
        graphics.drawCenteredString(font, title, width / 2, panelY + 10, UiTheme.TEXT);
        graphics.drawCenteredString(font, hint, width / 2, panelY + 24, UiTheme.MUTED);
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
