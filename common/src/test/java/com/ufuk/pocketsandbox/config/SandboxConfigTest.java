package com.ufuk.pocketsandbox.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SandboxConfigTest {
    @Test
    void missingFieldsKeepDefaults() {
        SandboxConfig.Values values = SandboxConfig.parse("{\"particles\": false}");
        assertFalse(values.particles);
        assertTrue(values.logicSimulation);
        assertEquals(600, values.maxParticles);
        assertEquals(5, values.defaultFloorSize);
    }

    @Test
    void valuesAreClamped() {
        SandboxConfig.Values values = SandboxConfig.parse("{\"maxParticles\": 999999, \"defaultFloorSize\": 8}");
        assertEquals(2000, values.maxParticles);
        assertEquals(7, values.defaultFloorSize, "8 is closest to 7 (ties go to the smaller size)");
        assertEquals(50, SandboxConfig.parse("{\"maxParticles\": -5}").maxParticles);
        assertEquals(51, SandboxConfig.parse("{\"defaultFloorSize\": 40}").defaultFloorSize);
        assertEquals(101, SandboxConfig.parse("{\"defaultFloorSize\": 500}").defaultFloorSize);
        assertEquals(15, SandboxConfig.parse("{\"defaultFloorSize\": 14}").defaultFloorSize);
    }

    @Test
    void brokenFileFallsBackAndIsRewritten(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(SandboxConfig.FILE_NAME);
        Files.writeString(file, "{ this is not json");
        SandboxConfig.load(dir);
        assertTrue(SandboxConfig.get().particles);
        String written = Files.readString(file);
        assertTrue(written.contains("\"logicSimulation\": true"), written);
    }

    @Test
    void missingFileIsCreated(@TempDir Path dir) {
        SandboxConfig.load(dir);
        assertTrue(Files.exists(dir.resolve(SandboxConfig.FILE_NAME)));
    }
}
