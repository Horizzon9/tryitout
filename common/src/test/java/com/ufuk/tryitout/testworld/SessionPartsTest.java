package com.ufuk.tryitout.testworld;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionPartsTest {
    @TempDir
    Path dir;

    @Test
    void noNestedTestWorlds() {
        assertTrue(SessionState.REAL.canEnter());
        for (SessionState state : new SessionState[] {SessionState.ENTERING, SessionState.IN_TEST, SessionState.EXITING}) {
            assertFalse(state.canEnter(), state.name());
            assertTrue(state.needsRecovery(), state.name());
        }
        assertTrue(SessionState.IN_TEST.canExit());
        assertFalse(SessionState.REAL.canExit());
        assertFalse(SessionState.ENTERING.canExit());
        assertFalse(SessionState.REAL.needsRecovery());
        assertEquals(SessionState.REAL, SessionState.byName("garbage"));
        assertEquals(SessionState.IN_TEST, SessionState.byName("IN_TEST"));
    }

    @Test
    void areasAreFarApartAndBorderHasMargin() {
        TestArea a = TestArea.forSlot(0, 25);
        TestArea b = TestArea.forSlot(1, 25);
        assertEquals(TestArea.SPACING, b.centerX() - a.centerX());
        assertEquals(25, a.maxX() - a.minX() + 1);
        double x = a.centerX() + 0.5;
        double floor = a.floorY() + 1;
        int ring = a.markerMaxX();
        assertEquals(a.maxX() + 1 + TestArea.LEAVE_MARGIN + 1, ring, "wall ring + 5 empty blocks + marker");
        assertFalse(a.isOutside(x, floor, a.centerZ()));
        assertFalse(a.isOutside(ring + 0.9, floor, a.centerZ()), "standing on the marker is fine");
        assertTrue(a.isOutside(ring + 1.0, floor, a.centerZ()), "past the marker");
        assertTrue(a.isOutside(a.markerMinX() - 0.1, floor, a.centerZ()));
        assertTrue(a.isOutside(x, a.bottomLimitY() - 0.1, a.centerZ()), "falling below");
        assertTrue(a.isOutside(x, a.markerTopY() + 1.0, a.centerZ()), "above the pillars");
        assertFalse(a.isOutside(x, a.markerTopY() + 0.5, a.centerZ()));
        assertFalse(a.isMarker(ring, a.floorY(), a.centerZ()), "no ring at floor level");
        assertTrue(a.isMarker(ring, a.floorY(), a.markerMaxZ()), "corner edge passes the floor level");
        assertTrue(a.isMarker(ring, a.markerTopY(), a.markerMaxZ()), "corner pillar top");
        assertTrue(a.isMarker(ring, a.markerTopY(), a.centerZ()), "top ring (ceiling side)");
        assertTrue(a.isMarker(a.centerX(), a.bottomLimitY(), a.markerMinZ()), "bottom ring");
        assertTrue(a.isMarker(a.markerMinX(), a.bottomLimitY(), a.markerMinZ()), "bottom corner");
        assertFalse(a.isMarker(ring, a.bottomLimitY() - 1, a.markerMaxZ()), "nothing below the box");
        assertTrue(a.isMarker(a.centerX(), a.markerTopY(), a.centerZ()), "cross on the top face");
        assertTrue(a.isMarker(a.centerX() + 3, a.bottomLimitY(), a.centerZ()), "cross on the bottom face");
        assertFalse(a.isMarker(a.centerX() + 3, a.markerTopY(), a.centerZ() + 3), "the top is not a full lid");
        assertFalse(a.isMarker(ring, a.floorY() + 1, a.centerZ()), "no wall of markers");
        assertFalse(a.isMarker(a.centerX(), a.floorY(), a.centerZ()));
        assertTrue(a.containsBlock(a.minX() - 1, a.floorY(), a.minZ()), "wall ring");
        assertFalse(a.containsBlock(a.minX() - 2, a.floorY(), a.minZ()));
    }

    @Test
    void slotsReuseTheLowestFreeIndex() {
        assertEquals(0, SlotAllocator.lowestFree(List.of()));
        assertEquals(2, SlotAllocator.lowestFree(List.of(0, 1, 3)));
        assertEquals(1, SlotAllocator.lowestFree(List.of(0, 2)));
    }

    @Test
    void buildBoxIsInsideTheMarkersAndCoversTheMargin() {
        TestArea area = TestArea.forSlot(2, 5);
        // Blocks placed next to the floor (in the 5-block margin) belong to the build box.
        assertTrue(area.buildMinX() < area.minX() - 1 && area.buildMaxZ() > area.maxZ() + 1);
        assertTrue(area.buildMinY() < area.floorY() && area.buildMaxY() > area.maxY());
        // No marker block inside the build box.
        for (int x = area.buildMinX(); x <= area.buildMaxX(); x++) {
            for (int z = area.buildMinZ(); z <= area.buildMaxZ(); z++) {
                for (int y = area.buildMinY(); y <= area.buildMaxY(); y++) {
                    assertFalse(area.isMarker(x, y, z), x + "," + y + "," + z);
                }
            }
        }
        // ...and nothing outside it is reachable without being sent back.
        assertTrue(area.isOutside(area.buildMaxX() + 2, area.floorY() + 1, area.centerZ()));
    }

    @Test
    void shellClosesTheBoxAndStepsCycleSizes() {
        TestArea area = TestArea.forSlot(3, 101);
        // Every face cell is shell, nothing inside the build box is.
        assertTrue(area.isShell(area.markerMinX(), area.floorY() + 3, area.centerZ()));
        assertTrue(area.isShell(area.centerX(), area.markerTopY(), area.centerZ()));
        assertTrue(area.isShell(area.centerX(), area.bottomLimitY(), area.centerZ()));
        assertFalse(area.isShell(area.buildMaxX(), area.buildMaxY(), area.buildMaxZ()));
        assertFalse(area.isShell(area.markerMaxX() + 1, area.floorY(), area.centerZ()));
        // Someone past the wall is set back just inside it, keeping the other coordinates.
        double[] back = area.clampInside(area.markerMaxX() + 3.0, area.floorY() + 2.0, area.centerZ() + 0.5);
        assertEquals(area.buildMaxX() + 0.5, back[0]);
        assertEquals(area.floorY() + 2.0, back[1]);
        assertEquals(51, TestArea.stepSize(25, 1));
        assertEquals(5, TestArea.stepSize(101, 1));
        assertEquals(101, TestArea.stepSize(5, -1));
        for (int size : TestArea.SIZES) {
            assertEquals(1, size % 2, "odd sizes only (a centre block)");
        }
    }

    @Test
    void atomicWriteKeepsBackupAndReaderFallsBack() throws IOException {
        Path file = dir.resolve("profiles").resolve("p.dat");
        AtomicFiles.write(file, "one".getBytes(StandardCharsets.UTF_8));
        AtomicFiles.write(file, "two".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals("one".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(AtomicFiles.backup(file)));
        assertEquals(Optional.of("two"), AtomicFiles.read(file, data -> new String(data, StandardCharsets.UTF_8)));
        // A broken main file falls back to the backup.
        Files.write(file, "broken".getBytes(StandardCharsets.UTF_8));
        Optional<String> read = AtomicFiles.read(file, data -> {
            String text = new String(data, StandardCharsets.UTF_8);
            if (text.equals("broken")) {
                throw new IOException("bad");
            }
            return text;
        });
        assertEquals(Optional.of("one"), read);
        assertFalse(Files.exists(file.resolveSibling("p.dat.tmp")));
        assertTrue(AtomicFiles.read(dir.resolve("missing.dat"), data -> "x").isEmpty());
    }
}
