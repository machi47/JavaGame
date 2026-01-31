package com.voxelgame.math;

/** Deterministic hash functions for world generation. */
public final class Hashing {

    private Hashing() {}

    public static int hash2D(int x, int z, long seed) {
        long h = seed ^ (x * 0x5DEECE66DL) ^ (z * 0x1234567890ABCDEFL);
        h = (h ^ (h >>> 16)) * 0x45D9F3BL;
        h = (h ^ (h >>> 16)) * 0x45D9F3BL;
        return (int) (h ^ (h >>> 16));
    }

    public static int hash3D(int x, int y, int z, long seed) {
        return hash2D(x ^ (y * 31), z, seed);
    }
}
