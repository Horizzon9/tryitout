package com.ufuk.pocketsandbox.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SandboxBoundsTest {
    @Test
    void fiveByFiveIsCentredOnOrigin() {
        SandboxBounds bounds = SandboxBounds.ofSize(5);
        assertEquals(-2, bounds.minX());
        assertEquals(2, bounds.maxX());
        assertEquals(-2, bounds.minZ());
        assertEquals(2, bounds.maxZ());
        assertTrue(bounds.contains(2, 0, -2));
        assertFalse(bounds.contains(3, 1, 0));
        assertFalse(bounds.contains(0, -1, 0));
        assertFalse(bounds.contains(0, SandboxBounds.DEFAULT_HEIGHT, 0));
    }

    @Test
    void floorLayerIsNotEditable() {
        SandboxBounds bounds = SandboxBounds.ofSize(7);
        assertFalse(bounds.editable(0, 0, 0));
        assertTrue(bounds.editable(0, 1, 0));
        assertTrue(bounds.editable(3, bounds.maxY(), -3));
    }

    @Test
    void sizesCycleUpToTwentyFive() {
        SandboxBounds bounds = SandboxBounds.ofSize(5);
        assertEquals(7, bounds.nextSize().size());
        assertEquals(9, bounds.nextSize().nextSize().size());
        assertEquals(15, SandboxBounds.ofSize(9).nextSize().size());
        assertEquals(25, SandboxBounds.ofSize(15).nextSize().size());
        assertEquals(51, SandboxBounds.ofSize(25).nextSize().size());
        assertEquals(5, SandboxBounds.ofSize(101).nextSize().size());
        assertEquals(SandboxBounds.MAX_HEIGHT, SandboxBounds.ofSize(101).height(), "big rooms stay low");
        assertEquals(26, SandboxBounds.ofSize(25).height(), "a 25 high wall fits above the floor");
    }

    @Test
    void heightFitsASquareWall() {
        assertEquals(SandboxBounds.DEFAULT_HEIGHT, SandboxBounds.ofSize(5).height());
        assertEquals(10, SandboxBounds.ofSize(9).height());
        assertTrue(SandboxBounds.ofSize(9).contains(4, 9, 4), "a 9 high wall fits above the floor");
        assertEquals(10, SandboxBounds.ofSize(7).nextSize().height());
    }

    @Test
    void evenOrTinySizesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SandboxBounds.ofSize(6));
        assertThrows(IllegalArgumentException.class, () -> new SandboxBounds(5, 1));
    }
}
