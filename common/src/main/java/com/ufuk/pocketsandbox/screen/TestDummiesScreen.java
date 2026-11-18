package com.ufuk.pocketsandbox.screen;

import com.ufuk.pocketsandbox.screen.ui.FlatButton;
import com.ufuk.pocketsandbox.screen.ui.UiTheme;
import com.ufuk.pocketsandbox.testworld.client.DummyReachRing;
import com.ufuk.pocketsandbox.testworld.client.TestClient;
import com.ufuk.pocketsandbox.testworld.net.TestActionPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The training dummies near you, nearest first: each with its distance and hits and its own remove button, plus
 * "Remove all". The list comes from what the client sees, so it follows at once when one is removed.
 */
public final class TestDummiesScreen extends Screen {
    private static final int W = 240;
    private static final int ROW = 20;
    private static final double SEARCH = 160.0;
    @Nullable
    private final Screen parent;
    private List<Entity> shown = List.of();
    private int panelY;
    private int panelH;

    public TestDummiesScreen(@Nullable Screen parent) {
        super(Component.translatable("pocketsandbox.test_dummies.title"));
        this.parent = parent;
    }

    private List<Entity> dummies() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return List.of();
        }
        List<Entity> found = new ArrayList<>(minecraft.level.getEntities(minecraft.player,
                minecraft.player.getBoundingBox().inflate(SEARCH), DummyReachRing::isDummy));
        found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(minecraft.player)));
        return found;
    }

    @Override
    protected void init() {
        shown = dummies();
        int visible = Math.max(1, Math.min(shown.size(), Math.max(1, (height - 110) / ROW)));
        panelH = 30 + visible * ROW + 8 + 20 + 4 + 20 + 10;
        panelY = Math.max(6, height / 2 - panelH / 2);
        int x = width / 2 - W / 2;
        int y = panelY + 28;
        for (int i = 0; i < Math.min(shown.size(), visible); i++) {
            Entity dummy = shown.get(i);
            String id = dummy.getUUID().toString();
            String current = label(dummy);
            addRenderableWidget(FlatButton.flat(Component.literal("✎"),
                            b -> minecraft.setScreen(new TextPromptScreen(this,
                                    Component.translatable("pocketsandbox.test_dummies.rename"),
                                    Component.translatable("pocketsandbox.test_dummies.rename.hint"), current, 24,
                                    text -> !text.contains("|"),
                                    text -> TestClient.request(TestActionPayload.Action.DUMMY_RENAME, id + "|" + text))))
                    .bounds(x + W - 42, y + i * ROW, 20, ROW - 2)
                    .tooltip(Component.translatable("pocketsandbox.test_dummies.rename")).build());
            addRenderableWidget(FlatButton.flat(Component.literal("✕"),
                            b -> TestClient.request(TestActionPayload.Action.DUMMY_REMOVE, id))
                    .bounds(x + W - 20, y + i * ROW, 20, ROW - 2).style(FlatButton.Style.DANGER)
                    .tooltip(Component.translatable("pocketsandbox.test_dummies.remove_one")).build());
        }
        y += visible * ROW + 8;
        addRenderableWidget(FlatButton.flat(Component.translatable("pocketsandbox.test_dummies.remove_all"),
                        b -> minecraft.setScreen(new ConfirmDialogScreen(this, parent,
                                Component.translatable("pocketsandbox.test_dummies.remove_all"),
                                Component.translatable("pocketsandbox.test_dummies.remove_all.confirm", shown.size()),
                                () -> TestClient.request(TestActionPayload.Action.DUMMY_REMOVE, ""))))
                .bounds(x, y, W, 20).style(FlatButton.Style.DANGER).active(!shown.isEmpty()).build());
        addRenderableWidget(FlatButton.flat(CommonComponents.GUI_BACK, b -> onClose()).bounds(x, y + 24, W, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        List<Entity> now = dummies();
        if (now.size() != shown.size()) {
            rebuildWidgets(); // one was removed (or added)
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - W / 2;
        UiTheme.panel(graphics, x - 8, panelY, W + 16, panelH);
        graphics.drawCenteredString(font, title, width / 2, panelY + 10, UiTheme.TEXT);
        int y = panelY + 28;
        if (shown.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("pocketsandbox.test_dummies.none"), width / 2,
                    y + 5, UiTheme.MUTED);
            return;
        }
        int visible = Math.max(1, Math.min(shown.size(), Math.max(1, (height - 110) / ROW)));
        for (int i = 0; i < Math.min(shown.size(), visible); i++) {
            Entity dummy = shown.get(i);
            int rowY = y + i * ROW;
            UiTheme.box(graphics, x, rowY, W - 46, ROW - 2, UiTheme.CARD);
            graphics.drawString(font, label(dummy), x + 5, rowY + 5, UiTheme.ACCENT, false);
            double distance = minecraft == null || minecraft.player == null ? 0 : dummy.distanceTo(minecraft.player);
            String info = Component.translatable("pocketsandbox.test_dummies.row",
                    String.format(java.util.Locale.ROOT, "%.1f", distance)).getString();
            Component name = dummy.getCustomName();
            if (name != null) {
                String text = name.getString();
                int dot = text.indexOf('·');
                if (dot >= 0) {
                    info = info + " · " + text.substring(dot + 1).trim();
                }
            }
            graphics.drawString(font, info, x + W - 50 - font.width(info), rowY + 5, UiTheme.MUTED, false);
        }
    }

    /** The dummy's name as shown on its tag (before the " · hits" part). */
    private static String label(Entity dummy) {
        Component name = dummy.getCustomName();
        if (name == null) {
            return "?";
        }
        String text = name.getString();
        int dot = text.indexOf('·');
        return (dot < 0 ? text : text.substring(0, dot)).trim();
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
