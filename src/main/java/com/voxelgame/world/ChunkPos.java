package com.voxelgame.world;

/**
 * Immutable 2D chunk coordinate (chunkX, chunkZ).
 * Used as map keys and for spatial queries.
 */
public record ChunkPos(int x, int z) {
    // TODO: distance methods, toWorldPos, hash optimization
}
