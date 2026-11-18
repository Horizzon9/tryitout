package com.ufuk.tryitout.screen;

import com.ufuk.tryitout.config.SandboxConfig;
import com.ufuk.tryitout.screen.ui.FlatButton;
import com.ufuk.tryitout.screen.ui.SettingRow;
import com.ufuk.tryitout.screen.ui.UiTheme;
import com.ufuk.tryitout.testworld.client.TestClient;
import com.ufuk.tryitout.testworld.net.TestActionPayload;
import com.ufuk.tryitout.testworld.net.TestSavesPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Save the test world under a name, or (when leaving) choose: save and leave / leave without saving / cancel,
 * with a switch to stop asking (it can be turned back on in the test world settings). A save is a snapshot of
 * that moment; what happens afterwards is only kept by saving again.
 */
public final class TestSaveScreen extends Screen {
    private static final int W = 220;
    @Nullable
    private final Screen parent;
    private final boolean leaving;
    private EditBox name;
    private int panelX;
    private int panelY;
    private int panelH;

    public TestSaveScreen(@Nullable Screen parent, boolean leaving) {
        super(Component.translatable(leaving ? "tryitout.test_save.leave_title" : "tryitout.test_save.title"));
        this.parent = parent;
        this.leaving = leaving;
    }

    @Override
    protected void init() {
        TestSavesPayload saves = TestClient.saves();
        int rows = leaving ? 5 : 3;
        panelH = 34 + 26 + rows * 24 + 6;
        panelX = width / 2 - W / 2 - 8;
        panelY = Math.max(6, height / 2 - panelH / 2);
        int x = width / 2 - W / 2;
        int y = panelY + 34;
        name = new EditBox(font, x, y, W, 20, Component.translatable("tryitout.test_save.name"));
        name.setMaxLength(32);
        name.setValue(saves.current().isEmpty() ? defaultName(saves) : saves.current());
        addRenderableWidget(name);
        setInitialFocus(name);
        y += 26;
        boolean canSave = saves.canSave();
        Button save = addRenderableWidget(FlatButton.flat(Component.translatable(
                leaving ? "tryitout.test_save.save_and_leave" : "tryitout.test_save.save"), b -> {
                    String value = name.getValue().trim();
                    if (value.isEmpty()) {
                        return;
                    }
                    TestClient.request(TestActionPayload.Action.SAVE, value);
                    if (leaving) {
                        TestClient.request(TestActionPayload.Action.EXIT, "");
                    }
                    onClose();
                }).bounds(x, y, W, 20).style(FlatButton.Style.ACCENT).build());
        save.active = canSave;
        y += 24;
        if (leaving) {
            addRenderableWidget(FlatButton.flat(Component.translatable("tryitout.test_save.leave_unsaved"), b -> {
                TestClient.request(TestActionPayload.Action.EXIT, "");
                onClose();
            }).bounds(x, y, W, 20).style(FlatButton.Style.DANGER).build());
            y += 24;
        }
        addRenderableWidget(FlatButton.flat(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x, y, W, 20).build());
        y += 24;
        if (leaving) {
            addRenderableWidget(SettingRow.toggle(x, y, W, 18,
                    () -> Component.translatable("tryitout.test_save.dont_ask"),
                    () -> !SandboxConfig.get().askOnExit,
                    () -> SandboxConfig.setAskOnExit(!SandboxConfig.get().askOnExit))
                    .tooltip(Component.translatable("tryitout.test_save.dont_ask.tip")));
        }
    }

    private static String defaultName(TestSavesPayload saves) {
        for (int i = 1; ; i++) {
            String candidate = "Test " + i;
            if (!saves.names().contains(candidate)) {
                return candidate;
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiTheme.panel(graphics, panelX, panelY, W + 16, panelH);
        TestSavesPayload saves = TestClient.saves();
        graphics.drawCenteredString(font, title, width / 2, panelY + 8, UiTheme.TEXT);
        Component info = saves.canSave()
                ? Component.translatable("tryitout.test_save.count", saves.names().size(), saves.limit())
                : Component.translatable("tryitout.test_save.cannot");
        graphics.drawCenteredString(font, info, width / 2, panelY + 20, UiTheme.MUTED);
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
