package com.voxelgame.world;

/**
 * Read-only world access interface for subsystems that need to query
 * block state without modifying it (e.g., meshing, rendering, physics).
 */
public interface WorldAccess {
    // TODO: getBlock(x, y, z), getChunk(cx, cz), isLoaded(cx, cz)
}
