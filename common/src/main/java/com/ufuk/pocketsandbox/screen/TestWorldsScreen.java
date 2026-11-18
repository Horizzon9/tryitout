package com.ufuk.pocketsandbox.screen;

import com.ufuk.pocketsandbox.screen.ui.FlatButton;
import com.ufuk.pocketsandbox.screen.ui.UiTheme;
import com.ufuk.pocketsandbox.testworld.SceneSnapshot;
import com.ufuk.pocketsandbox.testworld.client.TestClient;
import com.ufuk.pocketsandbox.testworld.net.TestActionPayload;
import com.ufuk.pocketsandbox.testworld.net.TestSavesPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Choose how to enter the test world: a new one with this scene's room, or one of the saved test worlds. */
public final class TestWorldsScreen extends Screen {
    private static final int W = 220;
    @Nullable
    private final Screen parent;
    private final ItemStack target;
    private final SceneSnapshot room;
    private List<String> shown = List.of();
    /** Floor size of the new test world: the scene's, or a bigger one (51 / 101) picked here. */
    private int size;
    private int panelX;
    private int panelY;
    private int panelH;

    public TestWorldsScreen(@Nullable Screen parent, ItemStack target, SceneSnapshot room) {
        super(Component.translatable("pocketsandbox.test_worlds.title"));
        this.parent = parent;
        this.target = target;
        this.room = room;
        this.size = room.size();
    }

    @Override
    protected void init() {
        TestClient.request(TestActionPayload.Action.LIST, "");
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        TestSavesPayload saves = TestClient.saves();
        shown = saves.names();
        int x = width / 2 - W / 2;
        int rows = Math.max(1, shown.size());
        panelH = 30 + 22 + 24 + 16 + rows * 22 + 8 + 20 + 8;
        panelX = x - 8;
        panelY = Math.max(6, height / 2 - panelH / 2);
        int y = panelY + 28;
        int[] sizes = com.ufuk.pocketsandbox.testworld.TestArea.SIZES;
        addRenderableWidget(com.ufuk.pocketsandbox.screen.ui.SettingRow.dropdown(x, y, W, 18,
                () -> Component.translatable("pocketsandbox.test_worlds.size"),
                () -> java.util.Arrays.stream(sizes).mapToObj(n -> (Component) Component.literal(n + " × " + n)).toList(),
                () -> java.util.Arrays.binarySearch(sizes, size),
                i -> size = sizes[i])
                .tooltip(Component.translatable("pocketsandbox.test_worlds.size.tip")));
        y += 22;
        addRenderableWidget(FlatButton.flat(Component.translatable("pocketsandbox.test_worlds.new"), b -> {
            TestClient.enter(target, new SceneSnapshot(size, room.floor(), room.walls(), room.ceiling(),
                    room.breakable(), room.blocks()));
            closeAll();
        }).bounds(x, y, W, 20).style(FlatButton.Style.ACCENT).build());
        y += 24 + 16;
        for (String name : shown) {
            addRenderableWidget(FlatButton.flat(Component.literal(name), b -> {
                TestClient.request(TestActionPayload.Action.LOAD, name);
                closeAll();
            }).bounds(x, y, W - 24, 18).tooltip(Component.translatable("pocketsandbox.test_worlds.load.tip")).build());
            addRenderableWidget(FlatButton.flat(Component.literal("✕"),
                    b -> TestClient.request(TestActionPayload.Action.DELETE, name))
                    .bounds(x + W - 20, y, 20, 18).style(FlatButton.Style.DANGER)
                    .tooltip(Component.translatable("pocketsandbox.test_worlds.delete")).build());
            y += 22;
        }
        if (shown.isEmpty()) {
            y += 22;
        }
        addRenderableWidget(FlatButton.flat(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(x, y + 8, W, 20).build());
    }

    private void closeAll() {
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!TestClient.saves().names().equals(shown)) {
            rebuild(); // the list arrived or a save was deleted
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiTheme.panel(graphics, panelX, panelY, W + 16, panelH);
        graphics.drawCenteredString(font, title, width / 2, panelY + 10, UiTheme.TEXT);
        int listTop = panelY + 28 + 22 + 24 + 4;
        graphics.drawString(font, Component.translatable("pocketsandbox.test_worlds.saved"), panelX + 8, listTop,
                UiTheme.ACCENT, false);
        if (shown.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("pocketsandbox.test_worlds.none"),
                    width / 2, listTop + 18, UiTheme.MUTED);
        }
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
