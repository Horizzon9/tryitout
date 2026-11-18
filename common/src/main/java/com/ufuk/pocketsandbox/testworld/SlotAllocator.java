package com.ufuk.pocketsandbox.testworld;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Picks the area slot for a new owner: the lowest index no one else has. Pure; unit tested. */
public final class SlotAllocator {
    private SlotAllocator() {
    }

    public static int lowestFree(Collection<Integer> taken) {
        Set<Integer> used = new HashSet<>(taken);
        int slot = 0;
        while (used.contains(slot)) {
            slot++;
        }
        return slot;
    }
}
