package com.ufuk.pocketsandbox.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.ufuk.pocketsandbox.PocketSandboxClient;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config/pocketsandbox.json}. Every switch exists so a player can turn off a part of the scene that
 * misbehaves with a particular mod pack, without losing the rest. Unknown or broken values fall back to the
 * defaults (and the file is rewritten with valid values).
 */
public final class SandboxConfig {
    public static final String FILE_NAME = "pocketsandbox.json";
    private static final int[] FLOOR_SIZES = com.ufuk.pocketsandbox.world.SandboxBounds.SIZES;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Values current = new Values();
    private static Path file;

    /** Plain data, read and written by Gson. Defaults are the field initialisers. */
    public static final class Values {
        /** Run server-side block logic (door halves, beds, redstone reactions) on the logic level. */
        public boolean logicSimulation = true;
        /** Client block-entity tickers (animated block entities). */
        public boolean blockEntityTicks = true;
        /** animateTick (torch flames, furnace smoke, modded ambient effects). */
        public boolean animateTicks = true;
        /** Scene particles. */
        public boolean particles = true;
        /** Upper bound of live scene particles (50..2000). */
        public int maxParticles = 600;
        /** Floor size when a block scene opens: 5, 7 or 9. */
        public int defaultFloorSize = 5;
        /** Show the controls line at the bottom of the sandbox. */
        public boolean showControls = true;
        /** Leaving the test world asks: save and leave / leave without saving / cancel. */
        public boolean askOnExit = true;

        /** Copy with every value clamped to its valid range. */
        public Values sanitized() {
            Values v = new Values();
            v.logicSimulation = logicSimulation;
            v.blockEntityTicks = blockEntityTicks;
            v.animateTicks = animateTicks;
            v.particles = particles;
            v.maxParticles = Math.max(50, Math.min(2000, maxParticles));
            v.defaultFloorSize = nearestFloorSize(defaultFloorSize);
            v.showControls = showControls;
            v.askOnExit = askOnExit;
            return v;
        }

        private static int nearestFloorSize(int size) {
            int best = FLOOR_SIZES[0];
            for (int candidate : FLOOR_SIZES) {
                if (Math.abs(candidate - size) < Math.abs(best - size)) {
                    best = candidate;
                }
            }
            return best;
        }
    }

    private SandboxConfig() {
    }

    public static Values get() {
        return current;
    }

    /** Reads (or creates) the file in {@code configDir}; never throws. */
    public static void load(Path configDir) {
        file = configDir.resolve(FILE_NAME);
        Values loaded = new Values();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Values parsed = GSON.fromJson(reader, Values.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (IOException | JsonParseException e) {
                PocketSandboxClient.LOGGER.warn("Pocket Sandbox: {} is invalid, using defaults", file, e);
            }
        }
        current = loaded.sanitized();
        save(file);
    }

    /** Turns "ask before leaving the test world" on or off and writes the file at once. */
    public static void setAskOnExit(boolean ask) {
        current.askOnExit = ask;
        if (file != null) {
            save(file);
        }
    }

    private static void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(current, writer);
            }
        } catch (IOException e) {
            PocketSandboxClient.LOGGER.warn("Pocket Sandbox: could not write {}", file, e);
        }
    }

    /** Parses JSON text (for tests): defaults for missing fields, clamped values. */
    public static Values parse(String json) {
        Values parsed = GSON.fromJson(json, Values.class);
        return (parsed == null ? new Values() : parsed).sanitized();
    }
}
