package com.ufuk.tryitout.screen.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One settings line: the name on the left and the value on the right, the whole line clickable.
 * <ul>
 *   <li>{@link #toggle}: an on/off switch (green pill with the knob on the right when on);</li>
 *   <li>{@link #choice}: "‹ value ›"; a click on the left half goes back, anywhere else forward (right click
 *       also goes back).</li>
 * </ul>
 * The value is read from a supplier every frame, so the line always shows what is true now (for the test world:
 * the server's answer).
 */
public final class SettingRow extends AbstractButton {
    private final Supplier<Component> name;
    @Nullable
    private final BooleanSupplier state;
    @Nullable
    private final Supplier<Component> value;
    private final Runnable forward;
    @Nullable
    private final Runnable back;
    private boolean backClick;
    /** Shows "value ▾" (the click opens a list) instead of "‹ value ›". */
    private boolean dropdown;

    private SettingRow(int x, int y, int w, int h, Supplier<Component> name, @Nullable BooleanSupplier state,
            @Nullable Supplier<Component> value, Runnable forward, @Nullable Runnable back) {
        super(x, y, w, h, name.get());
        this.name = name;
        this.state = state;
        this.value = value;
        this.forward = forward;
        this.back = back;
    }

    public static SettingRow toggle(int x, int y, int w, int h, Supplier<Component> name, BooleanSupplier state,
            Runnable flip) {
        return new SettingRow(x, y, w, h, name, state, null, flip, null);
    }

    public static SettingRow choice(int x, int y, int w, int h, Supplier<Component> name, Supplier<Component> value,
            Runnable next, @Nullable Runnable previous) {
        return new SettingRow(x, y, w, h, name, null, value, next, previous);
    }

    /**
     * "Name: value ▾": a click opens a list of {@code options} right under the line; picking one calls
     * {@code pick} with its index.
     */
    public static SettingRow dropdown(int x, int y, int w, int h, Supplier<Component> name,
            java.util.function.Supplier<java.util.List<Component>> options, java.util.function.IntSupplier selected,
            java.util.function.IntConsumer pick) {
        SettingRow[] self = new SettingRow[1];
        SettingRow row = new SettingRow(x, y, w, h, name, null, () -> {
            java.util.List<Component> list = options.get();
            int i = selected.getAsInt();
            return i >= 0 && i < list.size() ? list.get(i) : Component.literal("?");
        }, () -> {
            Minecraft mc = Minecraft.getInstance();
            SettingRow me = self[0];
            mc.setScreen(new com.ufuk.tryitout.screen.ChoiceListScreen(mc.screen, me.getX(), me.getY(),
                    me.getY() + me.getHeight(), Math.max(100, me.getWidth() / 2 + 20), options.get(),
                    selected.getAsInt(), pick));
        }, null);
        row.dropdown = true;
        self[0] = row;
        return row;
    }

    public SettingRow tooltip(Component text) {
        setTooltip(Tooltip.create(text));
        return this;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        backClick = back != null && value != null && mouseX < getX() + getWidth() - valueWidth() / 2.0
                && mouseX >= getX() + getWidth() - valueWidth() - 6;
        super.onClick(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && back != null && active && visible && isMouseOver(mouseX, mouseY)) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            back.run();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onPress() {
        if (backClick && back != null) {
            back.run();
        } else {
            forward.run();
        }
        backClick = false;
    }

    private int valueWidth() {
        Font font = Minecraft.getInstance().font;
        return value == null ? 22 : font.width(value.get()) + 18;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hot = active && isHoveredOrFocused();
        if (hot) {
            UiTheme.box(graphics, x, y, w, h, 0x30FFFFFF);
        }
        int textY = y + (h - 8) / 2;
        graphics.drawString(font, name.get(), x + 6, textY, active ? UiTheme.TEXT : UiTheme.DISABLED, false);
        if (state != null) {
            boolean on = state.getAsBoolean();
            int sw = 20;
            int sh = 10;
            int sx = x + w - sw - 6;
            int sy = y + (h - sh) / 2;
            UiTheme.box(graphics, sx, sy, sw, sh, on ? UiTheme.ON : UiTheme.OFF);
            int knob = on ? sx + sw - sh + 1 : sx + 1;
            UiTheme.box(graphics, knob, sy + 1, sh - 2, sh - 2, 0xFFF4F6F8);
        } else if (value != null) {
            Component shown = value.get();
            int vw = font.width(shown);
            int right = x + w - 6;
            int arrow = hot ? UiTheme.ACCENT : UiTheme.MUTED;
            if (dropdown) {
                graphics.drawString(font, "▾", right - font.width("▾"), textY, arrow, false);
                graphics.drawString(font, shown, right - font.width("▾") - 4 - vw, textY, UiTheme.ACCENT, false);
                return;
            }
            graphics.drawString(font, "›", right - font.width("›"), textY, arrow, false);
            graphics.drawString(font, shown, right - font.width("›") - 4 - vw, textY, UiTheme.ACCENT, false);
            if (back != null) {
                graphics.drawString(font, "‹", right - font.width("›") - 8 - vw - font.width("‹"), textY, arrow,
                        false);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        Component current = state != null
                ? Component.translatable(state.getAsBoolean() ? "options.on" : "options.off")
                : value != null ? value.get() : Component.empty();
        output.add(NarratedElementType.TITLE, Component.empty().append(name.get()).append(": ").append(current));
    }
}
