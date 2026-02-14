package com.voxelgame.world;

/** Global world dimension constants. */
public final class WorldConstants {

    public static final int CHUNK_SIZE = 16;
    public static final int WORLD_HEIGHT = 128;
    public static final int SEA_LEVEL = 64;
    public static final int CHUNK_VOLUME = CHUNK_SIZE * CHUNK_SIZE * WORLD_HEIGHT;

    // Sub-chunk section constants (for render optimization)
    public static final int SECTION_HEIGHT = 16;
    public static final int SECTIONS_PER_CHUNK = WORLD_HEIGHT / SECTION_HEIGHT; // 8
    public static final int SECTION_VOLUME = CHUNK_SIZE * CHUNK_SIZE * SECTION_HEIGHT; // 4096

    private WorldConstants() {}
}
