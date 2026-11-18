package com.ufuk.tryitout.screen;

import com.ufuk.tryitout.screen.ui.FlatButton;
import com.ufuk.tryitout.TryItOutClient;
import com.ufuk.tryitout.render.SceneRenderer;
import com.ufuk.tryitout.safety.FaultRegistry;
import com.ufuk.tryitout.safety.SandboxGuard;
import com.ufuk.tryitout.scene.SandboxCamera;
import com.ufuk.tryitout.scene.SandboxScene;
import com.ufuk.tryitout.scene.SceneMath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * The pocket test room, opened over the current screen. Owns the scene and its renderer: both are created in
 * {@link #init()} and freed in {@link #removed()} (rule 4). Esc returns to the screen it was opened from.
 *
 * <p>Mouse: left drag orbits, right drag pans, wheel zooms. A click (no drag): left breaks, right uses the
 * block or places the held palette item (Shift: always place). T rotates, R resets the camera, 1-9 pick,
 * L shows block light levels, N cycles day / dusk / night.
 */
public class SandboxScreen extends Screen {
    private static final int MARGIN = 24;
    private static final int HEADER = 26;
    private static final int FOOTER = 18;
    private static final int BUTTON_W = 64;
    private static final int BUTTON_H = 16;
    private static final double CLICK_SLOP = 3.0;
    /** Shader mods may draw over or skip our off-screen pass; say so instead of failing silently. */
    private static final boolean SHADER_MOD = dev.architectury.platform.Platform.isModLoaded("iris")
            || dev.architectury.platform.Platform.isModLoaded("oculus");
    /** Ambient presets: day, dusk, pitch-black night (sky contribution of the scene light map). */
    private static final float[] DAYLIGHT = {1.0F, 0.45F, 0.0F};
    private static final String[] DAYLIGHT_KEYS = {"day", "dusk", "night"};
    /** Vanilla sky darkening for the same presets (noon, around sunset, midnight). */
    private static final int[] SKY_DARKEN = {0, 6, 11};

    @Nullable
    private final Screen parent;
    private final ItemStack target;
    private final List<Button> buttons = new ArrayList<>();
    @Nullable
    private SandboxScene scene;
    @Nullable
    private SceneRenderer renderer;
    @Nullable
    private SandboxCamera camera;
    @Nullable
    private PaletteBar palette;
    @Nullable
    private Button sizeButton;
    @Nullable
    private Button daylightButton;
    @Nullable
    private Button lightButton;
    @Nullable
    private Button modeButton;
    @Nullable
    private Button wallButton;
    /** Wall sides (several at once), ceiling and floor block, as tick boxes. */
    private final DropdownMenu roomMenu = new DropdownMenu();
    /** The size button opens this list (5 x 5 ... 101 x 101) instead of stepping through the sizes. */
    private final DropdownMenu sizeMenu = new DropdownMenu();
    private int daylight;
    private boolean showLight;
    @Nullable
    private BlockHitResult hover;
    private int dragButton = -1;
    private double pressX;
    private double pressY;
    private boolean dragged;
    private int blockedAtOpen;
    /** True while a block GUI opened from the scene is shown on top: keep the scene alive. */
    private boolean suspended;

    public SandboxScreen(@Nullable Screen parent, ItemStack target) {
        this(parent, target, null);
    }

    /** Opens the scene with a room left in the test world instead of a fresh one. */
    public SandboxScreen(@Nullable Screen parent, ItemStack target,
            @Nullable com.ufuk.tryitout.testworld.SceneSnapshot restore) {
        super(Component.translatable("tryitout.screen.title"));
        this.parent = parent;
        this.target = target;
        this.restore = restore;
    }

    @Nullable
    private final com.ufuk.tryitout.testworld.SceneSnapshot restore;

    public ItemStack target() {
        return target;
    }

    @Override
    protected void init() {
        suspended = false;
        if (scene == null) {
            blockedAtOpen = SandboxGuard.blockedCount();
            try {
                scene = restore != null ? SandboxScene.restore(target, restore) : SandboxScene.open(target);
            } catch (RuntimeException | LinkageError e) {
                TryItOutClient.LOGGER.error("TryItOut: could not open the scene", e);
                scene = null;
            }
        }
        if (renderer == null) {
            renderer = new SceneRenderer();
        }
        if (camera == null) {
            camera = new SandboxCamera(SandboxCamera.distanceFor(currentSize()));
        }
        if (sizeButton == null && camera != null) {
            camera.setDefaultDistance(SandboxCamera.distanceFor(currentSize()));
            camera.reset();
        }
        if (palette == null) {
            palette = new PaletteBar(target);
        }
        int right = width - MARGIN - 4;
        int top = MARGIN + 5;
        buttons.clear();
        buttons.add(addWidget(FlatButton.flat(Component.translatable("tryitout.button.camera"),
                b -> camera.reset()).bounds(right - BUTTON_W, top, BUTTON_W, BUTTON_H).build()));
        sizeButton = addWidget(FlatButton.flat(sizeLabel(), b -> {
            roomMenu.close();
            sizeMenu.toggle();
        }).highlighted(sizeMenu::isOpen)
                .bounds(right - BUTTON_W * 2 - 4, top, BUTTON_W, BUTTON_H).build());
        buttons.add(sizeButton);
        buttons.add(addWidget(FlatButton.flat(Component.translatable("tryitout.button.reset"), b -> {
            if (scene != null) {
                scene.reset();
            }
        }).bounds(right - BUTTON_W * 3 - 8, top, BUTTON_W, BUTTON_H).build()));
        daylightButton = addWidget(FlatButton.flat(daylightLabel(), b -> cycleDaylight())
                .bounds(right - BUTTON_W * 4 - 12, top, BUTTON_W, BUTTON_H).build());
        buttons.add(daylightButton);
        lightButton = addWidget(FlatButton.flat(lightLabel(), b -> toggleLight())
                .bounds(right - BUTTON_W * 5 - 16, top, BUTTON_W, BUTTON_H).highlighted(() -> showLight)
                .tooltip(Component.translatable("tryitout.button.light.tip")).build());
        buttons.add(lightButton);
        wallButton = addWidget(FlatButton.flat(Component.translatable("tryitout.button.room"), b -> {
            sizeMenu.close();
            roomMenu.toggle();
        })
                .bounds(right - BUTTON_W * 7 - 24, top, BUTTON_W, BUTTON_H).highlighted(roomMenu::isOpen).build());
        buttons.add(wallButton);
        buildRoomMenu();
        if (com.ufuk.tryitout.testworld.client.TestClient.available()) {
            boolean inTest = com.ufuk.tryitout.testworld.client.TestClient.inTest();
            buttons.add(addWidget(FlatButton.flat(Component.translatable(inTest
                    ? "tryitout.button.test_exit" : "tryitout.button.test_world"),
                    b -> {
                        if (inTest) {
                            com.ufuk.tryitout.testworld.client.TestClient.requestExit(null);
                        } else if (scene != null) {
                            minecraft.setScreen(new TestWorldsScreen(this, target, scene.snapshot()));
                        }
                    })
                    .bounds(right - BUTTON_W * 8 - 28, top, BUTTON_W, BUTTON_H)
                    .style(inTest ? FlatButton.Style.DANGER : FlatButton.Style.ACCENT).build()));
        }
        roomMenu.layout(font, wallButton.getX() + wallButton.getWidth(), top + BUTTON_H, width);
        if (!sizeMenu.isBuilt()) {
            sizeMenu.title(Component.translatable("tryitout.menu.size"));
            for (int size : com.ufuk.tryitout.world.SandboxBounds.SIZES) {
                sizeMenu.option(Component.translatable("tryitout.button.size", size, size),
                        () -> currentSize() == size, () -> {
                            setSize(size);
                            sizeMenu.close();
                        });
            }
        }
        sizeMenu.layout(font, sizeButton.getX() + sizeButton.getWidth(), top + BUTTON_H, width);
        if (scene != null && scene.isBlock() && scene.weaponAvailable()) {
            modeButton = addWidget(FlatButton.flat(modeLabel(), b -> toggleMode())
                    .bounds(right - BUTTON_W * 6 - 20, top, BUTTON_W, BUTTON_H).highlighted(this::weapon).build());
            buttons.add(modeButton);
        }
    }

    /** Built once: the rows read the live scene state when drawn. */
    private void buildRoomMenu() {
        if (roomMenu.isBuilt()) {
            return;
        }
        roomMenu.title(Component.translatable("tryitout.menu.walls"));
        for (com.ufuk.tryitout.scene.WallSide side : com.ufuk.tryitout.scene.WallSide.values()) {
            roomMenu.option(Component.translatable("tryitout.menu.wall." + side.key()),
                    () -> scene != null && scene.hasWall(side), () -> {
                        if (scene != null) {
                            scene.toggleWall(side);
                        }
                    });
        }
        roomMenu.option(Component.translatable("tryitout.menu.ceiling"),
                () -> scene != null && scene.hasCeiling(), () -> {
                    if (scene != null) {
                        scene.toggleCeiling();
                    }
                });
        roomMenu.title(Component.translatable("tryitout.menu.floor"));
        for (com.ufuk.tryitout.scene.FloorType type : com.ufuk.tryitout.scene.FloorType.values()) {
            roomMenu.option(Component.translatable("tryitout.menu.floor." + type.key()),
                    () -> scene != null && scene.floor() == type, () -> {
                        if (scene != null) {
                            scene.setFloor(type);
                        }
                    });
        }
    }

    private boolean weapon() {
        return scene != null && scene.weaponMode();
    }

    private Component modeLabel() {
        return Component.translatable(weapon() ? "tryitout.button.mode.weapon" : "tryitout.button.mode.block");
    }

    /** Block editing <-> weapon test for block items (other items are always in weapon mode). */
    private void toggleMode() {
        if (scene == null) {
            return;
        }
        scene.setWeaponMode(!scene.weaponMode());
        if (modeButton != null) {
            modeButton.setMessage(modeLabel());
        }
        if (sizeButton != null) {
            sizeButton.setMessage(sizeLabel());
        }
        if (camera != null) {
            camera.setDefaultDistance(SandboxCamera.distanceFor(currentSize()));
            camera.reset();
        }
    }

    private Component daylightLabel() {
        return Component.translatable("tryitout.button.daylight." + DAYLIGHT_KEYS[daylight]);
    }

    private Component lightLabel() {
        return Component.translatable(showLight ? "tryitout.button.light.on" : "tryitout.button.light.off");
    }

    private void cycleDaylight() {
        daylight = (daylight + 1) % DAYLIGHT.length;
        if (daylightButton != null) {
            daylightButton.setMessage(daylightLabel());
        }
    }

    private void toggleLight() {
        showLight = !showLight;
        if (lightButton != null) {
            lightButton.setMessage(lightLabel());
        }
    }

    private int currentSize() {
        return scene == null ? 5 : scene.world().bounds().size();
    }

    private Component sizeLabel() {
        int size = currentSize();
        return Component.translatable("tryitout.button.size", size, size);
    }

    private void setSize(int size) {
        if (scene == null || camera == null) {
            return;
        }
        scene.setSize(size);
        camera.setDefaultDistance(SandboxCamera.distanceFor(currentSize()));
        camera.reset();
        if (sizeButton != null) {
            sizeButton.setMessage(sizeLabel());
        }
    }

    // ---- layout ----

    private int viewLeft() {
        return MARGIN + 4;
    }

    private int viewTop() {
        return MARGIN + HEADER;
    }

    private int viewRight() {
        return width - MARGIN - 4;
    }

    private int viewBottom() {
        return height - MARGIN - FOOTER;
    }

    private int viewWidth() {
        return viewRight() - viewLeft();
    }

    private int viewHeight() {
        return viewBottom() - viewTop();
    }

    private boolean inView(double x, double y) {
        return x >= viewLeft() && x < viewRight() && y >= viewTop() && y < viewBottom();
    }

    private int paletteLeft() {
        return viewLeft() + 6;
    }

    private int paletteTop() {
        return viewBottom() - PaletteBar.SLOT - 5;
    }

    // ---- picking ----

    private Matrix4f view() {
        return SceneMath.view(camera, scene.world().bounds().centerY());
    }

    @Nullable
    private BlockHitResult pick(double mouseX, double mouseY) {
        Vector3f[] ray = ray(mouseX, mouseY);
        return ray == null ? null : scene.editor().pick(ray[0], ray[1]).orElse(null);
    }

    /** Mouse ray in scene space, or null outside the scene image. */
    @Nullable
    private Vector3f[] ray(double mouseX, double mouseY) {
        if (scene == null || camera == null || !inView(mouseX, mouseY) || overPalette(mouseX, mouseY)) {
            return null;
        }
        Matrix4f projection = SceneMath.projection((float) viewWidth() / viewHeight());
        return SceneMath.ray(view(), projection, (mouseX - viewLeft()) / viewWidth(),
                (mouseY - viewTop()) / viewHeight());
    }

    /** Left click on a painting / frame / armour stand in front of any block takes it down. */
    private boolean removeDecoration(double mouseX, double mouseY, @Nullable BlockHitResult blockHit) {
        Vector3f[] ray = ray(mouseX, mouseY);
        if (ray == null) {
            return false;
        }
        var entityHit = scene.editor().pickEntity(ray[0], ray[1]);
        if (entityHit.isEmpty()) {
            return false;
        }
        double blockDistance = blockHit == null ? Double.MAX_VALUE
                : blockHit.getLocation().distanceTo(new net.minecraft.world.phys.Vec3(ray[0].x, ray[0].y, ray[0].z));
        if (entityHit.get().distance() > blockDistance + 0.01) {
            return false;
        }
        scene.editor().removeEntity(entityHit.get().entity());
        return true;
    }

    private boolean overPalette(double mouseX, double mouseY) {
        return palette != null && palette.slotAt(paletteLeft(), paletteTop(), mouseX, mouseY) >= 0;
    }

    // ---- rendering ----

    /**
     * The controls line as key chips: "[Wheel] zoom  [T] rotate ...", from the "key: what it does · ..." text.
     * What does not fit before {@code maxX} ends with "…".
     */
    private void renderControlChips(GuiGraphics graphics, int x, int y, int maxX) {
        String[] parts = Component.translatable("tryitout.screen.controls").getString().split(" · ");
        for (String part : parts) {
            int colon = part.indexOf(": ");
            String key = colon < 0 ? part : part.substring(0, colon);
            String what = colon < 0 ? "" : part.substring(colon + 2);
            int chipW = font.width(key) + 6;
            int width = chipW + 3 + font.width(what) + 8;
            if (x + width > maxX) {
                graphics.drawString(font, "…", x, y + 2, com.ufuk.tryitout.screen.ui.UiTheme.MUTED, false);
                return;
            }
            com.ufuk.tryitout.screen.ui.UiTheme.box(graphics, x, y, chipW, 11,
                    com.ufuk.tryitout.screen.ui.UiTheme.BUTTON);
            com.ufuk.tryitout.screen.ui.UiTheme.outline(graphics, x, y, chipW, 11,
                    com.ufuk.tryitout.screen.ui.UiTheme.BUTTON_BORDER);
            graphics.drawString(font, key, x + 3, y + 2, com.ufuk.tryitout.screen.ui.UiTheme.TEXT, false);
            graphics.drawString(font, what, x + chipW + 3, y + 2, com.ufuk.tryitout.screen.ui.UiTheme.MUTED, false);
            x += width;
        }
    }

    /**
     * The shader warning fits between the item name and the toolbar: shortened with "…" when the buttons leave too
     * little room (the full text shows on hover), only the "⚠" sign when there is almost none.
     */
    private void renderShaderWarning(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int limit = width;
        for (Button button : buttons) {
            if (button.visible && button.getY() < y + 10) {
                limit = Math.min(limit, button.getX());
            }
        }
        int room = limit - 6 - x;
        Component full = Component.translatable("tryitout.screen.shader_warning");
        String text = full.getString();
        if (font.width(text) > room) {
            String cut = font.plainSubstrByWidth(text, Math.max(0, room - font.width("…")));
            text = cut.length() <= 2 ? "⚠" : cut.stripTrailing() + "…";
        }
        if (room < font.width("⚠")) {
            return;
        }
        graphics.drawString(font, Component.literal(text).withStyle(ChatFormatting.GOLD), x, y, 0xFFFFFF);
        if (!text.equals(full.getString()) && mouseX >= x && mouseX < x + font.width(text)
                && mouseY >= y - 1 && mouseY < y + 9) {
            graphics.renderTooltip(font, full, mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = MARGIN;
        int top = MARGIN;
        int right = width - MARGIN;
        int bottom = height - MARGIN;
        // Not super.render(): widgets are drawn after the scene image (and the blur may run only once).
        renderBackground(graphics, mouseX, mouseY, partialTick);
        com.ufuk.tryitout.screen.ui.UiTheme.panel(graphics, left, top, right - left, bottom - top);
        graphics.fill(left + 1, top + 26, right - 1, top + 27, com.ufuk.tryitout.screen.ui.UiTheme.PANEL_BORDER);
        graphics.renderItem(target, left + 6, top + 5);
        graphics.drawString(font, target.getHoverName(), left + 26, top + 9, 0xFFFFFF);
        if (SHADER_MOD) {
            renderShaderWarning(graphics, left + 26 + font.width(target.getHoverName()) + 10, top + 9, mouseX, mouseY);
        }

        if (scene != null && renderer != null && camera != null) {
            hover = weapon() || roomMenu.isOpen() || sizeMenu.isOpen() ? null : pick(mouseX, mouseY);
            renderer.lightmap().setDaylight(DAYLIGHT[daylight]);
            try {
                renderer.render(graphics, scene, camera, viewLeft(), viewTop(), viewWidth(), viewHeight(), partialTick,
                        hover);
            } catch (RuntimeException | LinkageError | StackOverflowError e) {
                // The renderer restores GL state in finally; the frame is just skipped.
                if (scene.faults().record("scene-render", e)) {
                    TryItOutClient.LOGGER.warn("TryItOut: scene frame failed", e);
                }
            }
            if (showLight) {
                LightOverlay.render(graphics, font, scene, view(),
                        SceneMath.projection((float) viewWidth() / viewHeight()), viewLeft(), viewTop(), viewWidth(),
                        viewHeight(), SKY_DARKEN[daylight]);
                graphics.drawString(font, Component.translatable("tryitout.light.legend"), viewLeft() + 4,
                        viewTop() + 14, 0xFFFFFF);
            }
            renderPowerMarkers(graphics);
            if (weapon()) {
                renderWeapon(graphics, partialTick);
            } else {
                renderHoverInfo(graphics);
                if (palette != null) {
                    palette.render(graphics, font, paletteLeft(), paletteTop(), mouseX, mouseY);
                }
            }
        } else {
            graphics.drawCenteredString(font, Component.translatable("tryitout.screen.failed")
                    .withStyle(ChatFormatting.RED), width / 2, height / 2, 0xFFFFFF);
        }
        graphics.renderOutline(viewLeft() - 1, viewTop() - 1, viewWidth() + 2, viewHeight() + 2, 0xFF3A3A3A);
        for (Button button : buttons) {
            button.render(graphics, mouseX, mouseY, partialTick);
        }
        roomMenu.render(graphics, font, mouseX, mouseY);
        sizeMenu.render(graphics, font, mouseX, mouseY);
        if (com.ufuk.tryitout.config.SandboxConfig.get().showControls) {
            renderControlChips(graphics, left + 6, bottom - 14, right - 6);
        }
        renderBadges(graphics, mouseX, mouseY, right, bottom);
    }

    /** Small red markers on every virtually powered block. */
    private void renderPowerMarkers(GuiGraphics graphics) {
        if (scene == null || camera == null || scene.world().powered().isEmpty()) {
            return;
        }
        Matrix4f viewProjection = SceneMath.projection((float) viewWidth() / viewHeight()).mul(view());
        graphics.enableScissor(viewLeft(), viewTop(), viewRight(), viewBottom());
        for (var entry : scene.world().powered().entrySet()) {
            BlockPos pos = entry.getKey();
            org.joml.Vector4f clip = viewProjection.transform(
                    new org.joml.Vector4f(pos.getX() + 0.5F, pos.getY() + 1.15F, pos.getZ() + 0.5F, 1.0F));
            if (clip.w <= 0) {
                continue;
            }
            int sx = Math.round(viewLeft() + (clip.x / clip.w * 0.5F + 0.5F) * viewWidth());
            int sy = Math.round(viewTop() + (0.5F - clip.y / clip.w * 0.5F) * viewHeight());
            String label = "\u26A1" + entry.getValue();
            graphics.drawString(font, label, sx - font.width(label) / 2, sy - 4, 0xFF4040, true);
        }
        graphics.disableScissor();
    }

    private void renderWeapon(GuiGraphics graphics, float partialTick) {
        var rig = scene.rig();
        if (rig == null) {
            return;
        }
        long time = scene.world().gameTime();
        Matrix4f viewProjection = SceneMath.projection((float) viewWidth() / viewHeight()).mul(view());
        graphics.enableScissor(viewLeft(), viewTop(), viewRight(), viewBottom());
        WeaponPanel.renderRuler(graphics, font, rig, viewProjection, viewLeft(), viewTop(), viewWidth(), viewHeight());
        WeaponPanel.renderPopups(graphics, font, rig, time, partialTick, viewProjection, viewLeft(), viewTop(),
                viewWidth(), viewHeight());
        WeaponPanel.render(graphics, font, rig, time, viewRight(), viewTop());
        graphics.drawString(font, Component.translatable(rig.ranged() != null ? "tryitout.weapon.hint_ranged"
                : "tryitout.weapon.hint").withStyle(ChatFormatting.GRAY),
                viewLeft() + 4, viewBottom() - 12, 0xFFFFFF);
        graphics.disableScissor();
    }

    /** Name and position of the block under the mouse, top-left of the scene. */
    private void renderHoverInfo(GuiGraphics graphics) {
        if (hover == null || scene == null) {
            return;
        }
        BlockPos pos = hover.getBlockPos();
        BlockState state = scene.level().getBlockState(pos);
        Component text = Component.empty().append(state.getBlock().getName())
                .append(Component.literal("  " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                        .withStyle(ChatFormatting.GRAY));
        int power = scene.world().power(pos);
        if (power > 0) {
            text = Component.empty().append(text).append(Component.translatable("tryitout.power.hover", power)
                    .withStyle(ChatFormatting.RED));
        }
        graphics.drawString(font, text, viewLeft() + 4, viewTop() + 4, 0xFFFFFF);
    }

    /** Warning badges: faults (with tooltip) and blocked packets. */
    private void renderBadges(GuiGraphics graphics, int mouseX, int mouseY, int right, int bottom) {
        int x = right - 6;
        int y = bottom - 13;
        int blocked = SandboxGuard.blockedCount() - blockedAtOpen;
        if (blocked > 0) {
            Component text = Component.translatable("tryitout.badge.packets", blocked).withStyle(ChatFormatting.GOLD);
            x -= font.width(text);
            graphics.drawString(font, text, x, y, 0xFFFFFF);
            x -= 8;
        }
        if (scene != null && scene.ticker().skippedTicks() > 0) {
            Component skipped = Component.translatable("tryitout.badge.skipped_ticks", scene.ticker().skippedTicks())
                    .withStyle(ChatFormatting.GRAY);
            x -= font.width(skipped);
            graphics.drawString(font, skipped, x, y, 0xFFFFFF);
            x -= 8;
        }
        if (scene == null || scene.faults().count() == 0) {
            return;
        }
        FaultRegistry faults = scene.faults();
        Component text = Component.translatable("tryitout.badge.faults", faults.count()).withStyle(ChatFormatting.YELLOW);
        int w = font.width(text);
        x -= w;
        graphics.drawString(font, text, x, y, 0xFFFFFF);
        if (mouseX >= x && mouseX < x + w && mouseY >= y - 2 && mouseY < y + 10) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("tryitout.badge.faults_title").withStyle(ChatFormatting.YELLOW));
            for (FaultRegistry.Fault fault : faults.all()) {
                lines.add(Component.literal(fault.key() + ": " + fault.error()
                        + (fault.message().isEmpty() ? "" : " - " + fault.message())).withStyle(ChatFormatting.GRAY));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (roomMenu.isOpen() && (wallButton == null || !wallButton.isMouseOver(mouseX, mouseY))) {
            return roomMenu.mouseClicked(mouseX, mouseY);
        }
        if (sizeMenu.isOpen() && (sizeButton == null || !sizeButton.isMouseOver(mouseX, mouseY))) {
            return sizeMenu.mouseClicked(mouseX, mouseY);
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && camera != null && scene != null && inView(mouseX, mouseY)) {
            BlockHitResult hit = pick(mouseX, mouseY);
            if (hit != null) {
                net.minecraft.core.BlockPos pos = hit.getBlockPos();
                camera.setPivot(pos.getX(), (float) (pos.getY() + 0.5 - scene.world().bounds().centerY()), pos.getZ());
            }
            return true;
        }
        if (palette != null) {
            int slot = palette.slotAt(paletteLeft(), paletteTop(), mouseX, mouseY);
            if (slot >= 0) {
                if (slot == palette.plusSlot()) {
                    openBlockPicker();
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    palette.remove(slot);
                } else {
                    palette.select(slot);
                }
                return true;
            }
        }
        if (inView(mouseX, mouseY)) {
            dragButton = button;
            pressX = mouseX;
            pressY = mouseY;
            dragged = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == dragButton) {
            dragButton = -1;
            if (!dragged && inView(mouseX, mouseY)) {
                click(mouseX, mouseY, button);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** A click without dragging: left breaks, right uses / places. */
    private void click(double mouseX, double mouseY, int button) {
        if (weapon()) {
            var rig = scene.rig();
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                rig.swing(scene.world().gameTime(), hasShiftDown());
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                rig.rightClick(scene.world().gameTime());
            }
            return;
        }
        BlockHitResult hit = pick(mouseX, mouseY);
        if (scene != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && removeDecoration(mouseX, mouseY, hit)) {
            return;
        }
        if (hit == null || scene == null || camera == null || palette == null) {
            return;
        }
        scene.editor().aim(SceneMath.eye(view()), hit.getLocation());
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            scene.editor().breakBlock(hit.getBlockPos());
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            scene.player().takePendingMenu();
            scene.editor().useOrPlace(hit, palette.selected(), hasShiftDown(), hasControlDown());
            net.minecraft.world.MenuProvider menu = scene.player().takePendingMenu();
            if (menu != null) {
                SandboxMenus.open(this, menu, scene.player());
            }
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (camera != null && dragButton == button) {
            if (!dragged && Math.hypot(mouseX - pressX, mouseY - pressY) < CLICK_SLOP) {
                return true;
            }
            dragged = true;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !hasShiftDown()) {
                camera.orbit(dragX, dragY);
            } else {
                camera.pan(dragX, dragY, SceneMath.pixelsPerBlock(camera, viewHeight()));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void tick() {
        super.tick();
        if (scene != null) {
            // Last line of defence: nothing from the scene may crash the game (graceful degradation).
            try {
                scene.ticker().tick();
            } catch (RuntimeException | LinkageError | StackOverflowError e) {
                if (scene.faults().record("scene-tick", e)) {
                    TryItOutClient.LOGGER.warn("TryItOut: scene tick failed", e);
                }
            }
        }
    }

    /** V: toggles 15 / 0 virtual power on the hovered block. */
    private void togglePower() {
        if (scene == null || hover == null) {
            return;
        }
        BlockPos pos = hover.getBlockPos();
        scene.setPower(pos, scene.world().power(pos) > 0 ? 0 : 15);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scene != null && hover != null && hasControlDown() && scrollY != 0) {
            BlockPos pos = hover.getBlockPos();
            scene.setPower(pos, scene.world().power(pos) + (scrollY > 0 ? 1 : -1));
            return true;
        }
        if (camera != null && inView(mouseX, mouseY)) {
            camera.zoom(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R && camera != null) {
            camera.reset();
            return true;
        }
        if (weapon() && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            double step = scene.rig().distanceStep();
            scene.rig().moveDummy(keyCode == GLFW.GLFW_KEY_UP ? step : -step);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_V) {
            togglePower();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_L) {
            toggleLight();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_N) {
            cycleDaylight();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_T && scene != null && hover != null) {
            scene.editor().rotate(hover.getBlockPos());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_B && palette != null) {
            openBlockPicker();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9 && palette != null) {
            palette.select(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- lifecycle (rule 4) ----

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /** Block list (search, every placeable block): the picked block goes into the palette and is selected. */
    private void openBlockPicker() {
        if (minecraft == null) {
            return;
        }
        suspendForChild();
        minecraft.setScreen(new BlockPickerScreen(this, Component.translatable("tryitout.block_picker.scene"), id -> {
            if (id.isEmpty() || palette == null) {
                return;
            }
            net.minecraft.resources.ResourceLocation key = net.minecraft.resources.ResourceLocation.tryParse(id);
            if (key == null) {
                return;
            }
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(key).asItem();
            if (item != net.minecraft.world.item.Items.AIR) {
                palette.put(new net.minecraft.world.item.ItemStack(item));
            }
        }));
    }

    /** Called before a block GUI replaces this screen: {@link #removed()} must not free the scene. */
    void suspendForChild() {
        suspended = true;
    }

    /** The block GUI closed and this screen is shown again. */
    void resumeFromChild() {
        suspended = false;
    }

    /** The block GUI was replaced by something else: free everything now (rule 4). */
    void disposeSuspended() {
        suspended = false;
        removed();
    }

    @Override
    public void removed() {
        if (suspended) {
            return;
        }
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (scene != null) {
            scene.dispose();
            scene = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
