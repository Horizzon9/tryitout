package com.ufuk.pocketsandbox.testworld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandFilterTest {
    private static String check(String command) {
        return CommandFilter.refusal(command, List.of("create"), List.of("alex"), 10008.0, 65.0, 8.0);
    }

    @Test
    void simpleCommandsOnYourselfAreAllowed() {
        assertNull(check("give @s minecraft:diamond_sword"));
        assertNull(check("/gamemode survival"));
        assertNull(check("effect give @s minecraft:speed 30"));
        assertNull(check("minecraft:kill @s"));
        assertNull(check("setblock ~ ~1 ~ minecraft:stone"));
        assertNull(check("fill 10000 65 0 10010 70 10 minecraft:air"));
        assertNull(check("summon minecraft:zombie ~ ~ ~2"));
    }

    @Test
    void otherCommandsAreRefusedUnlessTheAdminAddedThem() {
        assertEquals("pocketsandbox.test.command.not_allowed", check("time set day"));
        assertEquals("pocketsandbox.test.command.not_allowed", check("weather rain"));
        assertEquals("pocketsandbox.test.command.not_allowed", check("execute in minecraft:overworld run tp @s 0 80 0"));
        assertEquals("pocketsandbox.test.command.not_allowed", check("gamerule keepInventory true"));
        assertNull(check("create ponder"));
    }

    @Test
    void otherPlayersAndSelectorsAreRefused() {
        assertEquals("pocketsandbox.test.command.only_self", check("give @a minecraft:diamond"));
        assertEquals("pocketsandbox.test.command.only_self", check("kill @e"));
        assertEquals("pocketsandbox.test.command.only_self", check("tp Alex"));
        assertEquals("pocketsandbox.test.command.only_self", check("give @p minecraft:stone"));
    }

    @Test
    void farAbsoluteCoordinatesAreRefused() {
        assertEquals("pocketsandbox.test.command.too_far", check("setblock 20000 65 8 minecraft:stone"));
        assertEquals("pocketsandbox.test.command.too_far", check("fill 10000 65 0 30000 70 10 minecraft:stone"));
        assertEquals("pocketsandbox.test.command.too_far", check("summon minecraft:zombie 0 65 0"));
    }
}
