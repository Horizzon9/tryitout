package com.ufuk.tryitout.testworld;

/**
 * Where a player's profile is, written to disk before every step so a half-done swap can be finished after a
 * crash. Entering is only allowed from {@link #REAL}; leaving only from {@link #IN_TEST}: no nested test worlds.
 */
public enum SessionState {
    /** Real profile on the player, not in the test world. */
    REAL,
    /** Real profile saved to disk, swap to the test profile under way. */
    ENTERING,
    /** Test profile on the player, inside the test world. */
    IN_TEST,
    /** Swap back to the real profile under way. */
    EXITING;

    public boolean canEnter() {
        return this == REAL;
    }

    public boolean canExit() {
        return this == IN_TEST;
    }

    /** True when the real profile on disk must be put back (anything but REAL). */
    public boolean needsRecovery() {
        return this != REAL;
    }

    public static SessionState byName(String name) {
        for (SessionState state : values()) {
            if (state.name().equals(name)) {
                return state;
            }
        }
        return REAL;
    }
}
