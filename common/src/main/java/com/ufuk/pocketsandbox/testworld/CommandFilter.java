package com.ufuk.pocketsandbox.testworld;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Which commands a player inside the test world may run (when the room's commands are on). A short, fixed list
 * of simple commands that only touch the player and the test world, plus the server admin's {@code extraCommands}.
 * Targets other than yourself and far absolute coordinates are refused, so a command cannot reach another
 * player or another test area. {@code time} and {@code weather} are not here: vanilla applies them to every
 * dimension, the test world has its own in the settings screen. Pure; unit tested.
 */
public final class CommandFilter {
    public static final Set<String> BUILT_IN = Set.of("gamemode", "give", "clear", "tp", "teleport", "effect",
            "enchant", "xp", "experience", "summon", "kill", "setblock", "fill");
    /** Commands whose arguments start with coordinates at these token positions (after the command name). */
    private static final int MAX_COORDINATE_DISTANCE = 64;

    private CommandFilter() {
    }

    /**
     * Why {@code command} is refused, as a message key; null when it may run.
     *
     * @param command     the command line without the leading slash
     * @param extra       extra command names from the server config (lower case)
     * @param others      names of every other online player (lower case)
     * @param px          the player's position, for absolute coordinates
     */
    public static String refusal(String command, Collection<String> extra, Collection<String> others,
            double px, double py, double pz) {
        String line = command.trim();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        String[] tokens = line.split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            return null;
        }
        String name = tokens[0].toLowerCase(Locale.ROOT);
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1); // minecraft:give -> give
        }
        boolean builtIn = BUILT_IN.contains(name);
        if (!builtIn && !extra.contains(name)) {
            return "pocketsandbox.test.command.not_allowed";
        }
        if (!builtIn) {
            return null; // the admin's own choice
        }
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i].toLowerCase(Locale.ROOT);
            if (token.startsWith("@") && !token.startsWith("@s")) {
                return "pocketsandbox.test.command.only_self";
            }
            if (others.contains(token)) {
                return "pocketsandbox.test.command.only_self";
            }
        }
        if (name.equals("setblock") || name.equals("fill") || name.equals("summon")) {
            int start = name.equals("summon") ? 2 : 1;
            int count = name.equals("fill") ? 6 : 3;
            double[] origin = {px, py, pz};
            for (int i = 0; i < count && start + i < tokens.length; i++) {
                if (tooFar(tokens[start + i], origin[i % 3])) {
                    return "pocketsandbox.test.command.too_far";
                }
            }
        }
        return null;
    }

    /** Absolute coordinates far from the player are refused; relative (~) and local (^) ones are fine. */
    static boolean tooFar(String token, double origin) {
        if (token.startsWith("~") || token.startsWith("^")) {
            return false;
        }
        try {
            return Math.abs(Double.parseDouble(token) - origin) > MAX_COORDINATE_DISTANCE;
        } catch (NumberFormatException e) {
            return false; // not a coordinate (block id, entity type...)
        }
    }
}
