package com.voxelgame.world;

/**
 * Immutable 2D chunk coordinate (chunkX, chunkZ).
 * Used as map keys and for spatial queries.
 */
public record ChunkPos(int x, int z) {

    public static ChunkPos fromWorldPos(float wx, float wz) {
        return new ChunkPos(
            Math.floorDiv((int) Math.floor(wx), WorldConstants.CHUNK_SIZE),
            Math.floorDiv((int) Math.floor(wz), WorldConstants.CHUNK_SIZE)
        );
    }

    public int distSq(ChunkPos other) {
        int dx = this.x - other.x;
        int dz = this.z - other.z;
        return dx * dx + dz * dz;
    }

    public int worldX() { return x * WorldConstants.CHUNK_SIZE; }
    public int worldZ() { return z * WorldConstants.CHUNK_SIZE; }
}
