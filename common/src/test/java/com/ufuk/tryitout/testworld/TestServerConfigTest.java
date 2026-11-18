package com.ufuk.tryitout.testworld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TestServerConfigTest {
    @Test
    void defaultsWhenEmpty() {
        TestServerConfig.Values values = TestServerConfig.parse("{}");
        assertEquals(101, values.areaSize);
        assertEquals(3, values.saveLimit);
        assertTrue(values.allowFriends);
        assertTrue(values.extraCommands.isEmpty());
    }

    @Test
    void valuesAreClamped() {
        TestServerConfig.Values values = TestServerConfig.parse("{\"areaSize\": 500, \"saveLimit\": -4}");
        assertEquals(TestServerConfig.MAX_AREA, values.areaSize);
        assertEquals(0, values.saveLimit);
        assertEquals(TestServerConfig.MIN_AREA, TestServerConfig.parse("{\"areaSize\": 1}").areaSize);
    }

    @Test
    void commandNamesAreCleaned() {
        TestServerConfig.Values values = TestServerConfig.parse(
                "{\"extraCommands\": [\" /Create \", \"create\", \"\", null, \"ponder\"]}");
        assertEquals(List.of("create", "ponder"), values.extraCommands);
    }

    @Test
    void oldFilesGetTheBigAreas() {
        // Written before version 2 with the old default: brought up to the new default.
        assertEquals(101, TestServerConfig.parse("{\"areaSize\": 25}").areaSize);
        // A value the owner chose stays, and so does 25 in an up-to-date file.
        assertEquals(15, TestServerConfig.parse("{\"areaSize\": 15}").areaSize);
        assertEquals(25, TestServerConfig.parse("{\"configVersion\": 2, \"areaSize\": 25}").areaSize);
    }
}
