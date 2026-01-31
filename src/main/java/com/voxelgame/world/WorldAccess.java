package com.voxelgame.world;

/**
 * Read-only world access interface for subsystems that need to query
 * block state without modifying it (e.g., meshing, rendering, physics).
 */
public interface WorldAccess {
    int getBlock(int x, int y, int z);
    Chunk getChunk(int cx, int cz);
    boolean isLoaded(int cx, int cz);
}
