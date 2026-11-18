package com.ufuk.pocketsandbox.safety;

import com.ufuk.pocketsandbox.PocketSandboxClient;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;

/**
 * Marks the stretches of render-thread code that run sandbox logic. While {@link #active()} is true the
 * {@code Connection#send} mixin drops every outgoing packet, so nothing a modded block, renderer or item
 * does inside the sandbox can ever reach the real server.
 *
 * <p>Always use {@link #enter()} in a try-with-resources block so the guard closes in {@code finally}.
 */
public final class SandboxGuard {
    private static int depth;
    private static final AtomicInteger BLOCKED = new AtomicInteger();

    private SandboxGuard() {
    }

    /** Opens the guard; nestable. */
    public static Scope enter() {
        depth++;
        return SandboxGuard::exit;
    }

    private static void exit() {
        if (depth > 0) {
            depth--;
        }
    }

    /** True only on the render thread while sandbox code is running. */
    public static boolean active() {
        return depth > 0 && Minecraft.getInstance().isSameThread();
    }

    /** Called by the mixin for every dropped packet. */
    public static void onBlocked(Object packet) {
        int count = BLOCKED.incrementAndGet();
        if (count <= 5 || count % 100 == 0) {
            PocketSandboxClient.LOGGER.warn("Pocket Sandbox blocked an outgoing packet from the sandbox ({}), total {}",
                    packet == null ? "null" : packet.getClass().getName(), count);
        }
    }

    public static int blockedCount() {
        return BLOCKED.get();
    }

    /** try-with-resources handle; close() never throws. */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
