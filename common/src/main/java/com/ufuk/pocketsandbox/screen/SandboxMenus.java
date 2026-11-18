package com.ufuk.pocketsandbox.screen;

import com.ufuk.pocketsandbox.PocketSandboxClient;
import com.ufuk.pocketsandbox.safety.SandboxGuard;
import com.ufuk.pocketsandbox.world.SandboxPlayer;
import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.events.client.ClientGuiEvent;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Opens the real GUI of a scene block (furnace, chest, modded machines) without the server: the menu is
 * created like on a server, bound to the scene block entity and the scene player's own inventory, and the
 * game's registered screen is shown for it. Slot clicks and buttons run locally on that menu (game mode
 * mixin); while the GUI is open, container / recipe / custom-payload packets are dropped (connection mixin),
 * everything else (keep-alive, movement) passes. Closing returns to the sandbox screen.
 */
public final class SandboxMenus {
    /** Above the server's 1..100 window id range, so it never matches a real container. */
    private static final int CONTAINER_ID = 1000;

    @Nullable
    private static Session session;

    private record Session(SandboxScreen parent, AbstractContainerMenu menu, Screen screen, SandboxPlayer player) {
    }

    private SandboxMenus() {
    }

    public static void init() {
        ClientGuiEvent.SET_SCREEN.register(SandboxMenus::onSetScreen);
    }

    /** True while a scene GUI is open. */
    public static boolean active() {
        return session != null;
    }

    /** Opens {@code provider}'s GUI over {@code parent}; false (with a log line) when it cannot be shown. */
    static boolean open(SandboxScreen parent, MenuProvider provider, SandboxPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        try (SandboxGuard.Scope ignored = SandboxGuard.enter()) {
            AbstractContainerMenu menu = provider.createMenu(CONTAINER_ID, player.getInventory(), player);
            if (menu == null) {
                return false;
            }
            Screen screen = createScreen(menu, player.getInventory(), provider.getDisplayName());
            if (screen == null) {
                return false;
            }
            player.containerMenu = menu;
            session = new Session(parent, menu, screen, player);
            parent.suspendForChild();
            mc.setScreen(screen);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            PocketSandboxClient.LOGGER.warn("Pocket Sandbox: could not open the GUI of {}", provider.getDisplayName().getString(), e);
            session = null;
            return false;
        }
    }

    /**
     * The game's registered screen for {@code menu}, built for our own menu instance. The lookup
     * ({@code MenuScreens#getConstructor}) and its factory type are private and named differently per loader,
     * so both are found by shape: the static method taking a {@link MenuType}, and the factory's single
     * method taking (menu, inventory, title).
     */
    @Nullable
    private static Screen createScreen(AbstractContainerMenu menu, Inventory inventory, Component title)
            throws ReflectiveOperationException {
        Method lookup = screenLookup();
        if (lookup == null) {
            return null;
        }
        Object factory = lookup.invoke(null, menu.getType());
        if (factory == null) {
            return null;
        }
        Method create = factoryMethod(lookup.getReturnType());
        if (create == null) {
            return null;
        }
        Object screen = create.invoke(factory, menu, inventory, title);
        return screen instanceof Screen result ? result : null;
    }

    @Nullable
    private static Method cachedLookup;

    @Nullable
    private static Method screenLookup() {
        if (cachedLookup != null) {
            return cachedLookup;
        }
        for (Method method : MenuScreens.class.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == MenuType.class && method.getReturnType().isInterface()) {
                method.setAccessible(true);
                cachedLookup = method;
                return method;
            }
        }
        PocketSandboxClient.LOGGER.warn("Pocket Sandbox: menu screen lookup not found; block GUIs disabled");
        return null;
    }

    @Nullable
    private static Method factoryMethod(Class<?> factoryType) {
        for (Method method : factoryType.getDeclaredMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (Modifier.isAbstract(method.getModifiers()) && params.length == 3 && params[1] == Inventory.class
                    && params[2] == Component.class) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    /** Game mode mixin: a slot click in the scene GUI. Returns true when handled (never sent). */
    public static boolean click(int containerId, int slotId, int button, ClickType type) {
        Session current = session;
        if (current == null || containerId != current.menu().containerId) {
            return false;
        }
        try (SandboxGuard.Scope ignored = SandboxGuard.enter()) {
            current.menu().clicked(slotId, button, type, current.player());
        } catch (RuntimeException | LinkageError e) {
            PocketSandboxClient.LOGGER.debug("Pocket Sandbox: slot click failed", e);
        }
        return true;
    }

    /** Game mode mixin: a GUI button (enchanting table, stonecutter, modded machines). */
    public static boolean button(int containerId, int buttonId) {
        Session current = session;
        if (current == null || containerId != current.menu().containerId) {
            return false;
        }
        try (SandboxGuard.Scope ignored = SandboxGuard.enter()) {
            current.menu().clickMenuButton(current.player(), buttonId);
        } catch (RuntimeException | LinkageError e) {
            PocketSandboxClient.LOGGER.debug("Pocket Sandbox: menu button failed", e);
        }
        return true;
    }

    /** Recipe book placement in a scene GUI: not supported, swallowed. */
    public static boolean placeRecipe(int containerId) {
        Session current = session;
        return current != null && containerId == current.menu().containerId;
    }

    /** LocalPlayer mixin: Esc / E in the scene GUI. Returns true when handled (back to the sandbox). */
    public static boolean closeToParent() {
        Session current = session;
        if (current == null) {
            return false;
        }
        session = null;
        try (SandboxGuard.Scope ignored = SandboxGuard.enter()) {
            current.menu().removed(current.player());
        } catch (RuntimeException | LinkageError e) {
            PocketSandboxClient.LOGGER.debug("Pocket Sandbox: closing the menu failed", e);
        }
        current.player().containerMenu = current.player().inventoryMenu;
        current.parent().resumeFromChild();
        Minecraft.getInstance().setScreen(current.parent());
        return true;
    }

    /** Packets a scene GUI could produce; dropped while one is open. */
    public static boolean isMenuPacket(Packet<?> packet) {
        return packet instanceof ServerboundContainerClickPacket
                || packet instanceof ServerboundContainerClosePacket
                || packet instanceof ServerboundContainerButtonClickPacket
                || packet instanceof ServerboundContainerSlotStateChangedPacket
                || packet instanceof ServerboundPlaceRecipePacket
                || packet instanceof ServerboundRecipeBookChangeSettingsPacket
                || packet instanceof ServerboundRecipeBookSeenRecipePacket
                || packet instanceof ServerboundSetCreativeModeSlotPacket
                || packet instanceof ServerboundRenameItemPacket
                || packet instanceof ServerboundSelectTradePacket
                || packet instanceof ServerboundSetBeaconPacket
                || packet instanceof ServerboundCustomPayloadPacket;
    }

    /** Any other screen replacing the scene GUI (server closed it, disconnect...) ends the session for good. */
    private static CompoundEventResult<Screen> onSetScreen(Screen next) {
        Session current = session;
        if (current != null && next != current.screen() && next != current.parent()) {
            session = null;
            current.parent().disposeSuspended();
        }
        return CompoundEventResult.pass();
    }
}
