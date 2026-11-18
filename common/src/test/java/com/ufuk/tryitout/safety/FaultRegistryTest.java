package com.ufuk.tryitout.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FaultRegistryTest {
    @Test
    void recordsOnlyTheFirstFailurePerKey() {
        FaultRegistry faults = new FaultRegistry();
        assertTrue(faults.record("be-render@0,1,0", new IllegalStateException("boom")));
        assertFalse(faults.record("be-render@0,1,0", new RuntimeException("again")));
        assertEquals(1, faults.count());
        assertTrue(faults.isFaulted("be-render@0,1,0"));
        assertEquals("IllegalStateException", faults.all().get(0).error());
        assertEquals("boom", faults.all().get(0).message());
    }

    @Test
    void clearAtRemovesOnlyThatPosition() {
        FaultRegistry faults = new FaultRegistry();
        faults.record("be-render@0,1,0", new RuntimeException());
        faults.record("mesh@0,1,0", new RuntimeException());
        faults.record("mesh@1,1,0", new RuntimeException());
        faults.clearAt("0,1,0");
        assertEquals(1, faults.count());
        assertTrue(faults.isFaulted("mesh@1,1,0"));
    }

    @Test
    void nullMessageBecomesEmpty() {
        FaultRegistry faults = new FaultRegistry();
        faults.record("x", new NullPointerException());
        assertEquals("", faults.all().get(0).message());
    }
}
