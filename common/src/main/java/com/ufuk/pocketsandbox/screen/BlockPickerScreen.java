package com.ufuk.pocketsandbox.screen;

import com.ufuk.pocketsandbox.screen.ui.FlatButton;
import com.ufuk.pocketsandbox.screen.ui.UiTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.GameMasterBlock;
import org.jetbrains.annotations.Nullable;

/**
 * Pick a block for the floor, walls or ceiling: a search box and a grid of every placeable block (modded ones too);
 * the block in your hand is offered first. "Default" goes back to the room's own block. Blocks the server refuses
 * (machines, command blocks, banned ones) are not shown.
 */
public final class BlockPickerScreen extends Screen {
    private static final int CELL = 20;
    @Nullable
    private final Screen parent;
    private final Consumer<String> onPick;
    private final List<BlockItem> all = new ArrayList<>();
    private final List<BlockItem> shown = new ArrayList<>();
    private EditBox search;
    private int scroll;
    private int gridX;
    private int gridY;
    private int columns;
    private int rows;

    public BlockPickerScreen(@Nullable Screen parent, Component title, Consumer<String> onPick) {
        super(title);
        this.parent = parent;
        this.onPick = onPick;
        for (var item : BuiltInRegistries.ITEM) {
            if (item instanceof BlockItem blockItem && !(blockItem.getBlock() instanceof EntityBlock)
                    && !(blockItem.getBlock() instanceof GameMasterBlock)) {
                all.add(blockItem);
            }
        }
    }

    @Override
    protected void init() {
        int panelW = Math.min(width - 20, 340);
        int left = (width - panelW) / 2;
        columns = Math.max(4, (panelW - 16) / CELL);
        rows = Math.max(2, (height - 120) / CELL);
        gridX = left + (panelW - columns * CELL) / 2;
        gridY = 56;
        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, left + 8, 30, panelW - 16, 18, Component.translatable("pocketsandbox.block_picker.search"));
        search.setHint(Component.translatable("pocketsandbox.block_picker.search"));
        search.setValue(query);
        search.setResponder(text -> {
            scroll = 0;
            filter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        int barY = gridY + rows * CELL + 8;
        int bw = (panelW - 16 - 8) / 3;
        addRenderableWidget(FlatButton.flat(Component.translatable("pocketsandbox.block_picker.held"), b -> pickHeld())
                .bounds(left + 8, barY, bw, 20).style(FlatButton.Style.ACCENT).build());
        addRenderableWidget(FlatButton.flat(Component.translatable("pocketsandbox.test_settings.block.default"), b -> {
            onPick.accept("");
            onClose();
        }).bounds(left + 8 + bw + 4, barY, bw, 20).build());
        addRenderableWidget(FlatButton.flat(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(left + 8 + 2 * (bw + 4), barY, bw, 20).build());
        filter();
    }

    private void filter() {
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (BlockItem item : all) {
            if (q.isEmpty() || item.getDescription().getString().toLowerCase(Locale.ROOT).contains(q)
                    || BuiltInRegistries.ITEM.getKey(item).toString().contains(q)) {
                shown.add(item);
            }
        }
    }

    private void pickHeld() {
        if (minecraft != null && minecraft.player != null
                && minecraft.player.getMainHandItem().getItem() instanceof BlockItem blockItem) {
            onPick.accept(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString());
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = indexAt(mouseX, mouseY);
        if (index >= 0) {
            onPick.accept(BuiltInRegistries.BLOCK.getKey(shown.get(index).getBlock()).toString());
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, (shown.size() + columns - 1) / columns - rows);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    private int indexAt(double mouseX, double mouseY) {
        if (mouseX < gridX || mouseY < gridY) {
            return -1;
        }
        int col = (int) ((mouseX - gridX) / CELL);
        int row = (int) ((mouseY - gridY) / CELL);
        if (col >= columns || row >= rows) {
            return -1;
        }
        int index = (scroll + row) * columns + col;
        return index < shown.size() ? index : -1;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelW = Math.min(width - 20, 340);
        int left = (width - panelW) / 2;
        UiTheme.panel(graphics, left, 6, panelW, gridY + rows * CELL + 8 + 20 + 8 - 6);
        graphics.drawCenteredString(font, title, width / 2, 14, UiTheme.TEXT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int hovered = indexAt(mouseX, mouseY);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int index = (scroll + row) * columns + col;
                if (index >= shown.size()) {
                    break;
                }
                int x = gridX + col * CELL;
                int y = gridY + row * CELL;
                UiTheme.box(graphics, x, y, CELL - 2, CELL - 2, index == hovered ? UiTheme.BUTTON_HOVER : UiTheme.CARD);
                graphics.renderItem(new ItemStack(shown.get(index)), x + 1, y + 1);
            }
        }
        if (shown.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("pocketsandbox.block_picker.none"), width / 2,
                    gridY + 20, UiTheme.MUTED);
        }
        if (hovered >= 0) {
            graphics.renderTooltip(font, shown.get(hovered).getDescription(), mouseX, mouseY);
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
