package com.ufuk.tryitout.screen;

import com.ufuk.tryitout.screen.ui.FlatButton;
import com.ufuk.tryitout.screen.ui.UiTheme;
import com.ufuk.tryitout.testworld.net.TestMaterialsPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** "What does this build need?": the blocks in the test area as items with counts, in columns; copy as text. */
public final class TestMaterialsScreen extends Screen {
    private static final int CELL_W = 150;
    private static final int CELL_H = 20;
    @Nullable
    private final Screen parent;
    private final List<ItemStack> items = new ArrayList<>();
    private final List<Integer> counts = new ArrayList<>();
    private int page;

    public TestMaterialsScreen(@Nullable Screen parent, TestMaterialsPayload list) {
        super(Component.translatable("tryitout.test_materials.title"));
        this.parent = parent;
        for (int i = 0; i < Math.min(list.items().size(), list.counts().size()); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.items().get(i));
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                items.add(new ItemStack(BuiltInRegistries.ITEM.get(id)));
                counts.add(list.counts().get(i));
            }
        }
    }

    private int columns() {
        return Math.max(1, (width - 40) / CELL_W);
    }

    private int rows() {
        return Math.max(1, (height - 90) / CELL_H);
    }

    private int perPage() {
        return columns() * rows();
    }

    @Override
    protected void init() {
        int y = height - 28;
        addRenderableWidget(FlatButton.flat(Component.literal("‹"), b -> page = Math.max(0, page - 1))
                .bounds(width / 2 - 160, y, 20, 20).build());
        addRenderableWidget(FlatButton.flat(Component.literal("›"), b -> {
            if ((page + 1) * perPage() < items.size()) {
                page++;
            }
        }).bounds(width / 2 - 136, y, 20, 20).build());
        addRenderableWidget(FlatButton.flat(Component.translatable("tryitout.test_materials.copy"), b -> copy())
                .bounds(width / 2 - 110, y, 110, 20).style(FlatButton.Style.ACCENT).build());
        addRenderableWidget(FlatButton.flat(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 + 4, y, 110, 20).build());
    }

    private void copy() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            text.append(counts.get(i)).append(" x ").append(items.get(i).getHoverName().getString()).append('\n');
        }
        if (minecraft != null) {
            minecraft.keyboardHandler.setClipboard(text.toString());
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiTheme.panel(graphics, 10, 6, width - 20, height - 12);
        graphics.drawCenteredString(font, title, width / 2, 14, UiTheme.TEXT);
        int pages = Math.max(1, (items.size() + perPage() - 1) / perPage());
        Component pageText = Component.literal((page + 1) + " / " + pages);
        graphics.drawString(font, pageText, width / 2 - 160 - font.width(pageText) - 6, height - 22,
                UiTheme.MUTED, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (items.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("tryitout.test_materials.empty"),
                    width / 2, 60, UiTheme.MUTED);
            return;
        }
        int total = counts.stream().mapToInt(Integer::intValue).sum();
        graphics.drawCenteredString(font, Component.translatable("tryitout.test_materials.total", total, items.size()),
                width / 2, 28, UiTheme.MUTED);
        int left = (width - columns() * CELL_W) / 2;
        int start = page * perPage();
        for (int i = start; i < Math.min(items.size(), start + perPage()); i++) {
            int slot = i - start;
            int x = left + (slot % columns()) * CELL_W;
            int y = 44 + (slot / columns()) * CELL_H;
            UiTheme.box(graphics, x, y, CELL_W - 4, CELL_H - 2, UiTheme.CARD);
            graphics.renderItem(items.get(i), x + 1, y + 1);
            String count = "×" + counts.get(i);
            graphics.drawString(font, count, x + 20, y + 5, UiTheme.ACCENT, false);
            String name = items.get(i).getHoverName().getString();
            int room = CELL_W - 4 - 24 - font.width(count) - 4;
            if (font.width(name) > room) {
                name = font.plainSubstrByWidth(name, Math.max(0, room - font.width("…"))) + "…";
            }
            graphics.drawString(font, name, x + 24 + font.width(count), y + 5, UiTheme.TEXT, false);
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
