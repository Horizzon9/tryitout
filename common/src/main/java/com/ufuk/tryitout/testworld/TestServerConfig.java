package com.ufuk.tryitout.testworld;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.ufuk.tryitout.TryItOut;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code config/tryitout-server.json}: rules of the server-side test world, set by the server admin (in
 * single player: by the player). Broken or missing values fall back to defaults and the file is rewritten.
 */
public final class TestServerConfig {
    public static final String FILE_NAME = "tryitout-server.json";
    public static final int MIN_AREA = 5;
    public static final int MAX_AREA = 128;
    public static final int MAX_SAVES = 20;
    /** Version of the file layout; older files are brought up to date when read. */
    public static final int CONFIG_VERSION = 2;
    /** areaSize every file written before version 2 had by default (the big 51 / 101 areas did not exist yet). */
    private static final int OLD_DEFAULT_AREA = 25;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Values current = new Values();

    /** Plain data, read and written by Gson. Defaults are the field initialisers. */
    public static final class Values {
        /** File layout version (missing in files written before 51 / 101 areas existed). */
        public int configVersion;
        /** Shown in the file; ignored when read. */
        public String _note = "extraCommands: extra command names allowed inside the test world (when the world owner"
                + " turns commands on). TryItOut cannot guarantee that a mod's command only affects the test"
                + " world: whoever adds one is responsible for it.";
        /**
         * Largest test world floor (5..128). Players pick 5/7/9/15/25/51/101 inside the test world; sizes above
         * this are not offered. Big areas take a moment to build.
         */
        public int areaSize = 101;
        /** Saved test worlds per player (0..20; 0 = saving off). */
        public int saveLimit = 3;
        /**
         * Whether players may switch their test area to "unlimited" (no border wall; they can still not go further
         * than 4900 blocks from its centre, so areas never meet). Off: the switch is locked in the game.
         */
        public boolean allowUnlimited = true;
        /** Whether owners may invite friends into their test world. */
        public boolean allowFriends = true;
        /** Command names allowed on top of the built-in simple list, e.g. "create". */
        public List<String> extraCommands = new ArrayList<>();
        /** Block ids that cannot be placed or used inside the test world (cross-dimension storage and the like). */
        public List<String> bannedBlocks = new ArrayList<>(List.of(
                "enderstorage:ender_chest", "enderstorage:ender_tank",
                "ae2:quantum_link", "ae2:quantum_ring",
                "refinedstorage:network_transmitter", "refinedstorage:network_receiver",
                "mekanism:quantum_entangloporter"));
        /** Item ids that cannot be used inside the test world. */
        public List<String> bannedItems = new ArrayList<>(List.of(
                "enderstorage:ender_pouch",
                "ae2:wireless_terminal", "ae2:wireless_crafting_terminal",
                "refinedstorage:wireless_grid", "refinedstorage:wireless_crafting_grid", "refinedstorage:portable_grid"));

        /** Copy with every value clamped to its valid range; list entries trimmed, lower-cased, de-duplicated. */
        public Values sanitized() {
            Values v = new Values();
            v.configVersion = CONFIG_VERSION;
            // A file from before version 2 still has the old default (25): the server owner never chose it, so the
            // big areas (51 / 101) become available. A value they did change is kept.
            int area = configVersion < 2 && areaSize == OLD_DEFAULT_AREA ? new Values().areaSize : areaSize;
            v.areaSize = Math.max(MIN_AREA, Math.min(MAX_AREA, area));
            v.saveLimit = Math.max(0, Math.min(MAX_SAVES, saveLimit));
            v.allowFriends = allowFriends;
            v.allowUnlimited = allowUnlimited;
            v.extraCommands = clean(extraCommands);
            v.bannedBlocks = clean(bannedBlocks);
            v.bannedItems = clean(bannedItems);
            return v;
        }

        private static List<String> clean(List<String> raw) {
            List<String> out = new ArrayList<>();
            if (raw == null) {
                return out;
            }
            for (String entry : raw) {
                if (entry == null) {
                    continue;
                }
                String value = entry.trim().toLowerCase(Locale.ROOT);
                if (value.startsWith("/")) {
                    value = value.substring(1);
                }
                if (!value.isEmpty() && !out.contains(value)) {
                    out.add(value);
                }
            }
            return out;
        }
    }

    private TestServerConfig() {
    }

    public static Values get() {
        return current;
    }

    /** Reads (or creates) the file in {@code configDir}; never throws. */
    public static void load(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        Values loaded = new Values();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Values parsed = GSON.fromJson(reader, Values.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (IOException | JsonParseException e) {
                TryItOut.LOGGER.warn("TryItOut: {} is invalid, using defaults", file, e);
            }
        }
        current = loaded.sanitized();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(current, writer);
            }
        } catch (IOException e) {
            TryItOut.LOGGER.warn("TryItOut: could not write {}", file, e);
        }
    }

    /** Parses JSON text (for tests): defaults for missing fields, clamped values. */
    public static Values parse(String json) {
        Values parsed = GSON.fromJson(json, Values.class);
        return (parsed == null ? new Values() : parsed).sanitized();
    }
}
