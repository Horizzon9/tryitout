package com.ufuk.tryitout.testworld;

/** Room block offsets packed into one int: x, z in -128..127, y in 0..255. Pure; unit tested. */
final class RoomPacking {
    private RoomPacking() {
    }

    static int pack(int x, int y, int z) {
        return ((x & 0xFF) << 16) | ((y & 0xFF) << 8) | (z & 0xFF);
    }

    static int unpackX(int packed) {
        return (byte) (packed >> 16);
    }

    static int unpackY(int packed) {
        return (packed >> 8) & 0xFF;
    }

    static int unpackZ(int packed) {
        return (byte) packed;
    }
}
