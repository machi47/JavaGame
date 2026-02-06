package com.voxelgame.world;

/**
 * Read-only world access interface for subsystems that need to query
 * block state and lighting without modifying it (e.g., meshing, rendering, physics).
 */
public interface WorldAccess {
    int getBlock(int x, int y, int z);
    
    /**
     * Get sky visibility (0.0 - 1.0) at world coordinates.
     * Returns 1.0 if the block has an unobstructed column to the sky, 0.0 otherwise.
     */
    float getSkyVisibility(int x, int y, int z);
    
    /**
     * Get sky light level (0-15) at world coordinates.
     * @deprecated Use {@link #getSkyVisibility(int, int, int)} for the new unified lighting model.
     */
    @Deprecated
    int getSkyLight(int x, int y, int z);
    
    int getBlockLight(int x, int y, int z);
    Chunk getChunk(int cx, int cz);
    boolean isLoaded(int cx, int cz);
}
