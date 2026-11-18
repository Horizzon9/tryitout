package com.ufuk.pocketsandbox.screen;

import com.ufuk.pocketsandbox.screen.ui.FlatButton;
import com.ufuk.pocketsandbox.screen.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** "Are you sure?" in the Pocket Sandbox style: Yes runs the action and goes to {@code afterYes}, No goes back. */
public final class ConfirmDialogScreen extends Screen {
    private static final int W = 220;
    @Nullable
    private final Screen back;
    @Nullable
    private final Screen afterYes;
    private final Component message;
    private final Runnable action;
    private int panelY;

    public ConfirmDialogScreen(@Nullable Screen back, @Nullable Screen afterYes, Component title, Component message,
            Runnable action) {
        super(title);
        this.back = back;
        this.afterYes = afterYes;
        this.message = message;
        this.action = action;
    }

    @Override
    protected void init() {
        int x = width / 2 - W / 2;
        panelY = height / 2 - 44;
        int bw = (W - 4) / 2;
        addRenderableWidget(FlatButton.flat(CommonComponents.GUI_YES, b -> {
            action.run();
            if (minecraft != null) {
                minecraft.setScreen(afterYes);
            }
        }).bounds(x, panelY + 56, bw, 20).style(FlatButton.Style.DANGER).build());
        addRenderableWidget(FlatButton.flat(CommonComponents.GUI_NO, b -> onClose())
                .bounds(x + bw + 4, panelY + 56, bw, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiTheme.panel(graphics, width / 2 - W / 2 - 8, panelY, W + 16, 86);
        graphics.drawCenteredString(font, title, width / 2, panelY + 10, UiTheme.TEXT);
        int y = panelY + 26;
        for (var line : font.split(message, W)) {
            graphics.drawCenteredString(font, line, width / 2, y, UiTheme.MUTED);
            y += 10;
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(back);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
