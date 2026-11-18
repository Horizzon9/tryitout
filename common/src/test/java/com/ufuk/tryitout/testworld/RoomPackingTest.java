package com.ufuk.tryitout.testworld;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RoomPackingTest {
    @Test
    void positionsRoundTripIncludingNegativeOffsets() {
        int[][] cases = {{0, 1, 0}, {-12, 26, 12}, {12, 1, -12}, {-2, 7, -2}};
        for (int[] c : cases) {
            int packed = RoomPacking.pack(c[0], c[1], c[2]);
            assertEquals(c[0], RoomPacking.unpackX(packed));
            assertEquals(c[1], RoomPacking.unpackY(packed));
            assertEquals(c[2], RoomPacking.unpackZ(packed));
        }
    }
}
