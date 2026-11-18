package com.ufuk.pocketsandbox.compat.ponder;

import com.ufuk.pocketsandbox.client.SandboxKeys;
import com.ufuk.pocketsandbox.screen.SandboxScreen;
import com.ufuk.pocketsandbox.testworld.client.TestClient;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientScreenInputEvent;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Two buttons in the bottom-left corner of Ponder's screen (the corner Ponder leaves empty), each with its key
 * shown under it like Ponder's own: "Test world" (T) enters the server-side test world with the scene exactly as
 * it is on screen right now (only when the server has Pocket Sandbox, not from inside a test world), "Pocket
 * scene" (G) opens it in the client-only pocket scene to change it.
 */
public final class PonderButtons {
    private static final int SIZE = 20;

    private PonderButtons() {
    }

    public static void init() {
        ClientGuiEvent.INIT_POST.register((screen, access) -> {
            if (!PonderCompat.isPonderScreen(screen) || Minecraft.getInstance().level == null) {
                return;
            }
            int y = screen.height - 20 - 31;
            int x = 31;
            if (testWorldAllowed()) {
                access.addRenderableWidget(new IconButton(x, y, new ItemStack(Items.GRASS_BLOCK), SandboxKeys.PONDER_TEST,
                        Component.translatable("pocketsandbox.ponder.test"), () -> openTestWorld(screen)));
                x += SIZE + 8;
            }
            access.addRenderableWidget(new IconButton(x, y, new ItemStack(Items.SPYGLASS), SandboxKeys.PONDER_SCENE,
                    Component.translatable("pocketsandbox.ponder.scene"), () -> openScene(screen)));
        });
        ClientScreenInputEvent.KEY_PRESSED_PRE.register((mc, screen, keyCode, scanCode, modifiers) -> {
            if (!PonderCompat.isPonderScreen(screen) || mc.level == null) {
                return EventResult.pass();
            }
            if (SandboxKeys.PONDER_TEST.matches(keyCode, scanCode) && testWorldAllowed()) {
                openTestWorld(screen);
                return EventResult.interruptTrue();
            }
            if (SandboxKeys.PONDER_SCENE.matches(keyCode, scanCode)) {
                openScene(screen);
                return EventResult.interruptTrue();
            }
            return EventResult.pass();
        });
    }

    private static boolean testWorldAllowed() {
        return TestClient.available() && !TestClient.inTest();
    }

    private static void openTestWorld(Screen ponder) {
        Optional<PonderCompat.Capture> capture = PonderCompat.capture(ponder);
        if (capture.isEmpty()) {
            failed();
            return;
        }
        TestClient.enter(capture.get().stack(), capture.get().room());
        Minecraft.getInstance().setScreen(null);
    }

    private static void openScene(Screen ponder) {
        Optional<PonderCompat.Capture> capture = PonderCompat.capture(ponder);
        if (capture.isEmpty()) {
            failed();
            return;
        }
        Minecraft.getInstance().setScreen(new SandboxScreen(ponder, capture.get().stack(), capture.get().room()));
    }

    private static void failed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("pocketsandbox.ponder.failed"), false);
        }
    }

    /** Ponder-style square button: dark box, item icon, the key's name underneath. */
    private static final class IconButton extends AbstractButton {
        private final ItemStack icon;
        private final net.minecraft.client.KeyMapping key;
        private final Runnable action;

        IconButton(int x, int y, ItemStack icon, net.minecraft.client.KeyMapping key, Component label, Runnable action) {
            super(x, y, SIZE, SIZE, label);
            this.icon = icon;
            this.key = key;
            this.action = action;
            setTooltip(Tooltip.create(label.copy().append(" [").append(key.getTranslatedKeyMessage()).append("]")));
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            int border = isHoveredOrFocused() ? 0xFFFFE08A : 0xFF8A7A5A;
            graphics.fill(x, y, x + SIZE, y + SIZE, 0xDD101018);
            graphics.renderOutline(x, y, SIZE, SIZE, border);
            graphics.renderItem(icon, x + 2, y + 2);
            Component name = key.getTranslatedKeyMessage();
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, name, x + SIZE / 2 - font.width(name) / 2, y + SIZE + 4, 0xFFDDDDDD, true);
            graphics.pose().popPose();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
