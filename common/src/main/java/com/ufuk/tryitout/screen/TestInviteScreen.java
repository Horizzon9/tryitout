package com.ufuk.tryitout.screen;

import com.ufuk.tryitout.screen.ui.FlatButton;
import com.ufuk.tryitout.testworld.client.TestClient;
import com.ufuk.tryitout.testworld.net.TestActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Invite a friend (by player name) into this test world; they get a clickable message to accept. */
public final class TestInviteScreen extends Screen {
    private static final int W = 200;
    @Nullable
    private final Screen parent;
    private EditBox name;
    private int panelY;

    public TestInviteScreen(@Nullable Screen parent) {
        super(Component.translatable("tryitout.test_invite.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - W / 2;
        panelY = height / 2 - 50;
        int y = panelY + 30;
        name = new EditBox(font, x, y, W, 20, Component.translatable("tryitout.test_invite.name"));
        name.setMaxLength(16);
        addRenderableWidget(name);
        setInitialFocus(name);
        addRenderableWidget(FlatButton.flat(Component.translatable("tryitout.test_invite.send"), b -> {
            if (!name.getValue().isBlank()) {
                TestClient.request(TestActionPayload.Action.INVITE, name.getValue().trim());
                onClose();
            }
        }).bounds(x, y + 28, W, 20).style(com.ufuk.tryitout.screen.ui.FlatButton.Style.ACCENT).build());
        addRenderableWidget(FlatButton.flat(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x, y + 52, W, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        com.ufuk.tryitout.screen.ui.UiTheme.panel(graphics, width / 2 - W / 2 - 8, panelY, W + 16, 112);
        graphics.drawCenteredString(font, title, width / 2, panelY + 10, com.ufuk.tryitout.screen.ui.UiTheme.TEXT);
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
