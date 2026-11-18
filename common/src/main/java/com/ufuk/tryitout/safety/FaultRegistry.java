package com.ufuk.tryitout.safety;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers which sandbox stage failed where (e.g. {@code be-render@1,1,1}). A faulted key is skipped from
 * then on, so the scene falls back to the static model instead of failing every frame. One per scene.
 */
public final class FaultRegistry {
    /** One recorded failure, shown in the warning badge tooltip. */
    public record Fault(String key, String error, String message) {
    }

    private final Map<String, Fault> faults = new LinkedHashMap<>();

    public boolean isFaulted(String key) {
        return faults.containsKey(key);
    }

    /** Records the first failure for {@code key}; returns true when it is new (so it is logged only once). */
    public boolean record(String key, Throwable error) {
        if (faults.containsKey(key)) {
            return false;
        }
        String message = error.getMessage() == null ? "" : error.getMessage();
        faults.put(key, new Fault(key, error.getClass().getSimpleName(), message));
        return true;
    }

    public int count() {
        return faults.size();
    }

    public List<Fault> all() {
        return Collections.unmodifiableList(new ArrayList<>(faults.values()));
    }

    public void clear() {
        faults.clear();
    }

    /** Clears every fault whose key ends with {@code @suffix} (used when a block is replaced). */
    public void clearAt(String suffix) {
        faults.keySet().removeIf(key -> key.endsWith("@" + suffix));
    }
}
