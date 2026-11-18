package com.ufuk.pocketsandbox.safety;

import com.ufuk.pocketsandbox.PocketSandboxClient;
import java.util.function.Supplier;

/**
 * Runs modded code inside the sandbox so that no failure can escape: exceptions, linkage errors and stack
 * overflows are caught, recorded in the scene's {@link FaultRegistry} and the key is skipped afterwards.
 * Out-of-memory and other VM errors are not swallowed. Every call also holds the {@link SandboxGuard}.
 */
public final class Safe {
    private Safe() {
    }

    /** Runs {@code action} unless {@code key} already failed; returns false when skipped or failed. */
    public static boolean run(FaultRegistry faults, String key, Runnable action) {
        if (faults.isFaulted(key)) {
            return false;
        }
        try (SandboxGuard.Scope ignored = SandboxGuard.enter()) {
            action.run();
            return true;
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            report(faults, key, e);
            return false;
        }
    }

    /**
     * For user actions (place, use, break): failures are recorded for the badge but the key is not skipped
     * next time, so the player can try again. Returns false on failure.
     */
    public static boolean attempt(FaultRegistry faults, String key, Runnable action) {
        try (SandboxGuard.Scope ignored = SandboxGuard.enter()) {
            action.run();
            return true;
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            report(faults, key, e);
            return false;
        }
    }

    /** Like {@link #run} but returns a value; {@code fallback} when skipped or failed. */
    public static <T> T get(FaultRegistry faults, String key, Supplier<T> action, T fallback) {
        if (faults.isFaulted(key)) {
            return fallback;
        }
        try (SandboxGuard.Scope ignored = SandboxGuard.enter()) {
            return action.get();
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            report(faults, key, e);
            return fallback;
        }
    }

    private static void report(FaultRegistry faults, String key, Throwable e) {
        if (faults.record(key, e)) {
            PocketSandboxClient.LOGGER.warn("Pocket Sandbox: '{}' failed, falling back to static rendering", key, e);
        }
    }
}
