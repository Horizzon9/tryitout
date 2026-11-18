package com.ufuk.pocketsandbox.compat;

import com.ufuk.pocketsandbox.PocketSandboxClient;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.item.ItemStack;

/**
 * Item overlays from other mods (JEI, EMI). Each bridge is registered by that mod's own plugin entrypoint,
 * so classes touching JEI/EMI only load when the mod is installed. Every call is guarded.
 */
public final class ItemSources {
    public interface Source {
        String name();

        /** The item under the mouse in this overlay, or {@link ItemStack#EMPTY}. */
        ItemStack hoveredStack();

        /** True while the overlay's search box has keyboard focus. */
        boolean searchFocused();
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    private ItemSources() {
    }

    public static void register(Source source) {
        SOURCES.removeIf(existing -> existing.name().equals(source.name()));
        SOURCES.add(source);
        PocketSandboxClient.LOGGER.info("Pocket Sandbox: {} hover support enabled", source.name());
    }

    public static void unregister(String name) {
        SOURCES.removeIf(existing -> existing.name().equals(name));
    }

    public static ItemStack hoveredStack() {
        for (Source source : SOURCES) {
            try {
                ItemStack stack = source.hoveredStack();
                if (stack != null && !stack.isEmpty()) {
                    return stack;
                }
            } catch (RuntimeException | LinkageError e) {
                PocketSandboxClient.LOGGER.debug("{} hovered stack lookup failed", source.name(), e);
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean searchFocused() {
        for (Source source : SOURCES) {
            try {
                if (source.searchFocused()) {
                    return true;
                }
            } catch (RuntimeException | LinkageError e) {
                PocketSandboxClient.LOGGER.debug("{} search focus check failed", source.name(), e);
            }
        }
        return false;
    }
}
