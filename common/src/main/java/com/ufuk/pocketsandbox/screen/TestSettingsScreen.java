package com.ufuk.pocketsandbox.screen;

import com.ufuk.pocketsandbox.config.SandboxConfig;
import com.ufuk.pocketsandbox.screen.ui.FlatButton;
import com.ufuk.pocketsandbox.screen.ui.SettingRow;
import com.ufuk.pocketsandbox.screen.ui.UiTheme;
import com.ufuk.pocketsandbox.screen.ui.WallCompass;
import com.ufuk.pocketsandbox.testworld.TestArea;
import com.ufuk.pocketsandbox.testworld.client.TestClient;
import com.ufuk.pocketsandbox.testworld.net.TestActionPayload;
import com.ufuk.pocketsandbox.testworld.net.TestSettingsPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Settings of the test room while inside it (opened from the inventory), laid out as cards: Room (size, walls,
 * ceiling, unbreakable), Blocks (floor / wall / ceiling block), Display (light level, ask before leaving), World
 * (time, weather, game mode) and Rules (nature rules, commands); actions at the bottom. Every change is sent to
 * the server and the lines show the server's answer.
 */
public final class TestSettingsScreen extends Screen {
    private static final String[] TIME_KEYS = {"day", "dusk", "night", "midnight", "cycle"};
    private static final String[] WEATHER_KEYS = {"clear", "rain", "thunder"};
    private static final String[] MODE_KEYS = {"survival", "creative", "adventure", "spectator"};
    private static final int GAP = 6;
    private static final int CARD_HEAD = 19;

    private record Card(Component title, int x, int y, int w, int h) {
    }

    @Nullable
    private final Screen parent;
    private final List<Card> cards = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int rowH;
    /** Health and "when it dies" only matter while dummies can die. */
    private SettingRow[] dummyOptions = new SettingRow[0];
    @Nullable
    private SettingRow unlimitedRow;
    private int facingX;
    private int facingY;

    public TestSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("pocketsandbox.test_settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        cards.clear();
        // Three columns when there is room (short screen), two otherwise.
        boolean three = width - 16 >= 450;
        panelW = Math.min(width - 16, three ? 600 : 470);
        panelX = (width - panelW) / 2;
        int columns = three ? 3 : 2;
        int colW = (panelW - GAP * (columns + 1)) / columns;
        int[] colX = new int[columns];
        for (int i = 0; i < columns; i++) {
            colX[i] = panelX + GAP + i * (colW + GAP);
        }
        // Tallest column: three columns 8 rows / 2 cards, two columns 11 rows / 3 cards.
        int tallestRows = three ? 8 : 11;
        // (the Room card has 5 rows now: three columns -> Room + Blocks = 8 as well)
        int tallestCards = three ? 2 : 3;
        int available = height - 8 - 22 - (20 + GAP * 2) - 8;
        rowH = Math.max(12, Math.min(18, (available - tallestCards * (CARD_HEAD + GAP + 4)) / tallestRows));
        int top = 8 + 22;
        panelY = 8;
        // Card order: Room, Blocks | Display, World | Rules, Dummy  (two columns: Room, Blocks, Display | World, Rules, Dummy)
        int[] colY = new int[columns];
        java.util.Arrays.fill(colY, top);

        int c = 0;
        colY[c] = roomCard(colX[c], colY[c], colW) + GAP;
        colY[c] = blocksCard(colX[c], colY[c], colW) + GAP;
        c = three ? 1 : 0;
        colY[c] = displayCard(colX[c], colY[c], colW) + GAP;
        c = 1;
        colY[c] = worldCard(colX[c], colY[c], colW) + GAP;
        c = three ? 2 : 1;
        colY[c] = rulesCard(colX[c], colY[c], colW) + GAP;
        colY[c] = dummyCard(colX[c], colY[c], colW) + GAP;
        int contentBottom = 0;
        for (int y : colY) {
            contentBottom = Math.max(contentBottom, y);
        }

        // ---- one action bar: save, materials, invite | leave, close ----
        int barY = contentBottom;
        int innerW = panelW - GAP * 2;
        int bw = (innerW - 4 * 4) / 5;
        int bx = panelX + GAP;
        addRenderableWidget(FlatButton.flat(Component.translatable("pocketsandbox.test_save.save"),
                        b -> minecraft.setScreen(new TestSaveScreen(this, false)))
                .bounds(bx, barY, bw, 20).style(FlatButton.Style.ACCENT)
                .tooltip(Component.translatable("pocketsandbox.test_settings.save.tip")).build());
        addRenderableWidget(FlatButton.flat(Component.translatable("pocketsandbox.test_settings.materials"),
                b -> TestClient.request(TestActionPayload.Action.MATERIALS, "")).bounds(bx + (bw + 4), barY, bw, 20).build());
        addRenderableWidget(FlatButton.flat(Component.translatable("pocketsandbox.test_settings.invite"),
                b -> minecraft.setScreen(new TestInviteScreen(this))).bounds(bx + 2 * (bw + 4), barY, bw, 20).build());
        addRenderableWidget(FlatButton.flat(Component.translatable("pocketsandbox.button.test_exit"),
                b -> TestClient.requestExit(null)).bounds(bx + 3 * (bw + 4), barY, bw, 20).style(FlatButton.Style.DANGER)
                .build());
        addRenderableWidget(FlatButton.flat(Component.translatable("pocketsandbox.test_settings.close"),
                b -> onClose()).bounds(bx + 4 * (bw + 4), barY, bw, 20).build());
        panelH = barY + 20 + GAP - panelY;
    }

    private int roomCard(int x, int y, int colW) {
        y = card(x, y, colW, 5, "pocketsandbox.test_settings.card.room");
        int ry = y - 5 * rowH - 4;
        addRenderableWidget(SettingRow.dropdown(x + 2, ry, colW - 4, rowH,
                () -> Component.translatable("pocketsandbox.test_settings.size"),
                TestSettingsScreen::sizeOptions,
                () -> java.util.Arrays.binarySearch(TestArea.SIZES, current().size()),
                i -> setSize(TestArea.SIZES[i]))
                .tooltip(Component.translatable("pocketsandbox.test_settings.size.tip")));
        ry += rowH;
        // Walls: a compass that turns with you (the letter on top is the wall in front of you).
        int compass = 3 * rowH;
        addRenderableWidget(new WallCompass(x + 4, ry, compass,
                side -> (current().walls() & (1 << side.ordinal())) != 0,
                side -> send(s -> new TestSettingsPayload(s.walls() ^ (1 << side.ordinal()), s.flags(), s.time(),
                        s.weather(), s.gameMode()))));
        int sideX = x + 4 + compass + 4;
        int sideW = x + colW - 2 - sideX;
        facingX = sideX + 6;
        facingY = ry + (rowH - 8) / 2;
        flag(sideX, ry + rowH, sideW, "pocketsandbox.menu.ceiling", TestSettingsPayload.CEILING);
        addRenderableWidget(SettingRow.toggle(sideX, ry + 2 * rowH, sideW, rowH,
                () -> Component.translatable("pocketsandbox.test_settings.locked"),
                () -> current().has(TestSettingsPayload.LOCKED),
                () -> send(s -> s.toggled(TestSettingsPayload.LOCKED)))
                .tooltip(Component.translatable("pocketsandbox.test_settings.locked.tip")));
        SettingRow unlimited = SettingRow.toggle(x + 2, ry + 3 * rowH, colW - 4, rowH,
                () -> Component.translatable("pocketsandbox.test_settings.unlimited"),
                () -> current().has(TestSettingsPayload.UNLIMITED),
                () -> send(s -> s.toggled(TestSettingsPayload.UNLIMITED)))
                .tooltip(Component.translatable("pocketsandbox.test_settings.unlimited.tip"));
        addRenderableWidget(unlimited);
        unlimitedRow = unlimited;
        return y;
    }

    private int blocksCard(int x, int y, int colW) {
        y = card(x, y, colW, 3, "pocketsandbox.test_settings.card.blocks");
        int ry = y - 3 * rowH - 4;
        blockRow(x + 2, ry, colW - 4, "floor", TestActionPayload.Action.SET_FLOOR);
        blockRow(x + 2, ry + rowH, colW - 4, "wall", TestActionPayload.Action.SET_WALL);
        blockRow(x + 2, ry + 2 * rowH, colW - 4, "ceiling", TestActionPayload.Action.SET_CEILING);
        return y;
    }

    private int displayCard(int x, int y, int colW) {
        y = card(x, y, colW, 3, "pocketsandbox.test_settings.card.display");
        int ry = y - 3 * rowH - 4;
        addRenderableWidget(SettingRow.toggle(x + 2, ry, colW - 4, rowH,
                () -> Component.translatable("pocketsandbox.test_settings.light"),
                TestClient::showLight, TestClient::toggleLight));
        addRenderableWidget(SettingRow.toggle(x + 2, ry + rowH, colW - 4, rowH,
                () -> Component.translatable("pocketsandbox.test_settings.ask_on_exit"),
                () -> SandboxConfig.get().askOnExit,
                () -> SandboxConfig.setAskOnExit(!SandboxConfig.get().askOnExit))
                .tooltip(Component.translatable("pocketsandbox.test_settings.ask_on_exit.tip")));
        addRenderableWidget(SettingRow.toggle(x + 2, ry + 2 * rowH, colW - 4, rowH,
                () -> Component.translatable("pocketsandbox.test_settings.border_edges"),
                () -> !current().has(TestSettingsPayload.BORDER_HIDDEN),
                () -> send(s -> s.toggled(TestSettingsPayload.BORDER_HIDDEN)))
                .tooltip(Component.translatable("pocketsandbox.test_settings.border_edges.tip")));
        return y;
    }

    private int worldCard(int x, int y, int colW) {
        y = card(x, y, colW, 3, "pocketsandbox.test_settings.card.world");
        int ry = y - 3 * rowH - 4;
        addRenderableWidget(SettingRow.dropdown(x + 2, ry, colW - 4, rowH,
                () -> Component.translatable("pocketsandbox.test_settings.time.name"),
                () -> labels("pocketsandbox.test_settings.time.", TIME_KEYS), () -> current().time(),
                i -> send(s -> withTime(s, i - s.time()))));
        addRenderableWidget(SettingRow.dropdown(x + 2, ry + rowH, colW - 4, rowH,
                () -> Component.translatable("pocketsandbox.test_settings.weather.name"),
                () -> labels("pocketsandbox.test_settings.weather.", WEATHER_KEYS), () -> current().weather(),
                i -> send(s -> withWeather(s, i - s.weather()))));
        addRenderableWidget(SettingRow.dropdown(x + 2, ry + 2 * rowH, colW - 4, rowH,
                () -> Component.translatable("pocketsandbox.test_settings.mode.name"),
                () -> labels("pocketsandbox.test_settings.mode.", MODE_KEYS), () -> current().gameMode(),
                i -> send(s -> withMode(s, i - s.gameMode()))));
        return y;
    }

    private int rulesCard(int x, int y, int colW) {
        y = card(x, y, colW, 5, "pocketsandbox.test_settings.card.rules");
        int ry = y - 5 * rowH - 4;
        flag(x + 2, ry, colW - 4, "pocketsandbox.test_settings.mob_spawning", TestSettingsPayload.MOB_SPAWNING);
        flag(x + 2, ry + rowH, colW - 4, "pocketsandbox.test_settings.fire_spread", TestSettingsPayload.FIRE_SPREAD);
        flag(x + 2, ry + 2 * rowH, colW - 4, "pocketsandbox.test_settings.random_ticks", TestSettingsPayload.RANDOM_TICKS);
        flag(x + 2, ry + 3 * rowH, colW - 4, "pocketsandbox.test_settings.keep_inventory",
                TestSettingsPayload.KEEP_INVENTORY);
        flag(x + 2, ry + 4 * rowH, colW - 4, "pocketsandbox.test_settings.commands", TestSettingsPayload.COMMANDS);
        return y;
    }

    private int dummyCard(int x, int y, int colW) {
        y = card(x, y, colW, 3, "pocketsandbox.test_settings.card.dummy");
        int ry = y - 3 * rowH - 4;
        int dw = (colW - 4 - 4) / 2;
        addRenderableWidget(FlatButton.flat(Component.translatable("pocketsandbox.test_settings.dummy_add"),
                        b -> TestClient.request(TestActionPayload.Action.DUMMY_ADD, ""))
                .bounds(x + 2, ry, dw, rowH).tooltip(Component.translatable("pocketsandbox.test_settings.dummy.tip"))
                .build());
        addRenderableWidget(FlatButton.flat(Component.translatable("pocketsandbox.test_settings.dummy_remove"),
                        b -> minecraft.setScreen(new TestDummiesScreen(this)))
                .bounds(x + 2 + dw + 4, ry, dw, rowH).build());
        int halfW = (colW - 4) / 2;
        addRenderableWidget(SettingRow.toggle(x + 2, ry + rowH, halfW, rowH,
                () -> Component.translatable("pocketsandbox.test_settings.dummy.killable"),
                () -> current().has(TestSettingsPayload.DUMMY_KILLABLE),
                () -> send(s -> s.toggled(TestSettingsPayload.DUMMY_KILLABLE)))
                .tooltip(Component.translatable("pocketsandbox.test_settings.dummy.killable.tip")));
        Runnable typeHealth = () -> minecraft.setScreen(new TextPromptScreen(this,
                Component.translatable("pocketsandbox.test_settings.dummy.health.title"),
                Component.translatable("pocketsandbox.test_settings.dummy.health.hint", TestSettingsPayload.MAX_DUMMY_HP),
                String.valueOf(Math.max(1, current().dummyHp())), 5, TextPromptScreen.digits(),
                text -> {
                    if (!text.isBlank()) {
                        TestClient.request(TestActionPayload.Action.DUMMY_HEALTH, text);
                    }
                }));
        SettingRow health = SettingRow.choice(x + 2 + halfW, ry + rowH, colW - 4 - halfW, rowH,
                () -> Component.translatable("pocketsandbox.test_settings.dummy.health"),
                () -> Component.literal(Math.max(1, current().dummyHp()) + " HP"),
                typeHealth, null)
                .tooltip(Component.translatable("pocketsandbox.test_settings.dummy.health.tip"));
        addRenderableWidget(health);
        SettingRow onDeath = SettingRow.dropdown(x + 2, ry + 2 * rowH, colW - 4, rowH,
                () -> Component.translatable("pocketsandbox.test_settings.dummy.on_death"),
                () -> java.util.List.of(Component.translatable("pocketsandbox.test_settings.dummy.respawn"),
                        Component.translatable("pocketsandbox.test_settings.dummy.remove")),
                () -> current().has(TestSettingsPayload.DUMMY_REMOVE) ? 1 : 0,
                i -> {
                    if ((i == 1) != current().has(TestSettingsPayload.DUMMY_REMOVE)) {
                        send(s -> s.toggled(TestSettingsPayload.DUMMY_REMOVE));
                    }
                });
        addRenderableWidget(onDeath);
        dummyOptions = new SettingRow[] {health, onDeath};
        return y;
    }

    /** Adds a card with {@code rows} rows at (x, y); returns the y just below it. */
    private int card(int x, int y, int w, int rows, String titleKey) {
        int h = CARD_HEAD + rows * rowH + 4;
        cards.add(new Card(Component.translatable(titleKey), x, y, w, h));
        return y + h;
    }

    private static TestSettingsPayload current() {
        return TestClient.settings().sanitized();
    }

    private static void send(UnaryOperator<TestSettingsPayload> change) {
        TestClient.sendSettings(change.apply(current()));
    }

    private void flag(int x, int y, int w, String key, int flag) {
        addRenderableWidget(SettingRow.toggle(x, y, w, rowH, () -> Component.translatable(key),
                () -> current().has(flag), () -> send(s -> s.toggled(flag))));
    }

    private static TestSettingsPayload withTime(TestSettingsPayload s, int step) {
        return new TestSettingsPayload(s.walls(), s.flags(), Math.floorMod(s.time() + step,
                TestSettingsPayload.TIME_CYCLE + 1), s.weather(), s.gameMode());
    }

    private static TestSettingsPayload withWeather(TestSettingsPayload s, int step) {
        return new TestSettingsPayload(s.walls(), s.flags(), s.time(),
                Math.floorMod(s.weather() + step, TestSettingsPayload.WEATHERS), s.gameMode());
    }

    private static TestSettingsPayload withMode(TestSettingsPayload s, int step) {
        return new TestSettingsPayload(s.walls(), s.flags(), s.time(), s.weather(),
                Math.floorMod(s.gameMode() + step, TestSettingsPayload.GAME_MODES));
    }

    private static java.util.List<Component> labels(String prefix, String[] keys) {
        java.util.List<Component> list = new java.util.ArrayList<>();
        for (String key : keys) {
            list.add(Component.translatable(prefix + key));
        }
        return list;
    }

    /** Every size, the ones above the server's limit greyed with a lock. */
    private static java.util.List<Component> sizeOptions() {
        int max = current().maxSize() > 0 ? current().maxSize() : 25;
        java.util.List<Component> list = new java.util.ArrayList<>();
        for (int size : TestArea.SIZES) {
            list.add(size <= max ? Component.literal(size + " × " + size)
                    : Component.literal("🔒 " + size + " × " + size).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        return list;
    }

    private static void setSize(int size) {
        TestSettingsPayload s = current();
        int max = s.maxSize() > 0 ? s.maxSize() : 25;
        if (size > max || size == s.size()) {
            return;
        }
        TestClient.sendSettings(new TestSettingsPayload(s.walls(), s.flags(), s.time(), s.weather(), s.gameMode())
                .withSize(size));
    }

    /** Next / previous size this server allows (big ones are skipped when the server caps them). */
    private static void changeSize(int step) {
        TestSettingsPayload s = current();
        int max = s.maxSize() > 0 ? s.maxSize() : 25;
        int size = s.size() > 0 ? s.size() : 5;
        for (int i = 0; i < TestArea.SIZES.length; i++) {
            size = TestArea.stepSize(size, step);
            if (size <= max) {
                break;
            }
        }
        TestClient.sendSettings(new TestSettingsPayload(s.walls(), s.flags(), s.time(), s.weather(), s.gameMode())
                .withSize(size));
    }

    /** "Floor: Snow Block": click opens the block picker, right click (or the left arrow) goes back to default. */
    private void blockRow(int x, int y, int w, String part, TestActionPayload.Action action) {
        addRenderableWidget(SettingRow.choice(x, y, w, rowH,
                () -> Component.translatable("pocketsandbox.test_settings.block." + part),
                () -> blockName(switch (part) {
                    case "floor" -> current().floorBlock();
                    case "wall" -> current().wallBlock();
                    default -> current().ceilingBlock();
                }),
                () -> minecraft.setScreen(new BlockPickerScreen(this,
                        Component.translatable("pocketsandbox.block_picker.title." + part),
                        id -> TestClient.request(action, id))),
                () -> TestClient.request(action, ""))
                .tooltip(Component.translatable("pocketsandbox.test_settings.block.tip")));
    }

    private static Component blockName(String id) {
        ResourceLocation location = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.BLOCK.containsKey(location)) {
            return Component.translatable("pocketsandbox.test_settings.block.default");
        }
        return BuiltInRegistries.BLOCK.get(location).getName();
    }

    @Override
    public void tick() {
        super.tick();
        if (unlimitedRow != null) {
            unlimitedRow.active = current().has(TestSettingsPayload.UNLIMITED_ALLOWED);
        }
        boolean killable = current().has(TestSettingsPayload.DUMMY_KILLABLE);
        for (SettingRow row : dummyOptions) {
            row.active = killable;
        }
        if (!TestClient.inTest()) {
            onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiTheme.panel(graphics, panelX, panelY, panelW, panelH);
        graphics.drawString(font, title, panelX + GAP + 2, panelY + 8, UiTheme.TEXT, false);
        Component shared = Component.translatable("pocketsandbox.test_settings.shared");
        boolean fits = font.width(title) + font.width(shared) + 24 <= panelW;
        Component note = fits ? shared : Component.literal("ⓘ");
        graphics.drawString(font, note, panelX + panelW - GAP - 2 - font.width(note), panelY + 8, UiTheme.MUTED, false);
        for (Card card : cards) {
            UiTheme.card(graphics, font, card.title(), card.x(), card.y(), card.w(), card.h());
        }
        // "Facing: North" next to the compass.
        graphics.drawString(font, Component.translatable("pocketsandbox.test_settings.facing",
                Component.translatable("pocketsandbox.menu.wall." + WallCompass.facing().key())
                        .withColor(UiTheme.ACCENT & 0xFFFFFF)), facingX, facingY, UiTheme.MUTED, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component shared = Component.translatable("pocketsandbox.test_settings.shared");
        if (font.width(title) + font.width(shared) + 24 > panelW) {
            int x = panelX + panelW - GAP - 2 - font.width("ⓘ");
            if (mouseX >= x - 2 && mouseX <= x + font.width("ⓘ") + 2 && mouseY >= panelY + 6 && mouseY <= panelY + 17) {
                graphics.renderTooltip(font, font.split(shared, 200), mouseX, mouseY);
            }
        }
    }

    /** The key that opened this screen closes it again. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (TestClient.OPEN_TEST_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
