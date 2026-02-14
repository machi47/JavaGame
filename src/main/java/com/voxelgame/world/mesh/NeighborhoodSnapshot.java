package com.voxelgame.world.mesh;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Immutable snapshot of a chunk and its neighbors for meshing.
 * Eliminates all map lookups during the meshing hot path.
 * 
 * Resolved once at mesh job start, then all block reads are direct array access.
 */
public final class NeighborhoodSnapshot {
    
    // Center chunk (always non-null)
    public final Chunk center;
    
    // Cardinal neighbors (may be null if unloaded)
    public final Chunk nx;  // x-1
    public final Chunk px;  // x+1
    public final Chunk nz;  // z-1
    public final Chunk pz;  // z+1
    
    // Chunk coordinates (for world-space calculations)
    public final int cx, cz;
    
    // Direct byte array references for zero-overhead block access
    private final byte[] centerBlocks;
    private final byte[] nxBlocks;
    private final byte[] pxBlocks;
    private final byte[] nzBlocks;
    private final byte[] pzBlocks;
    
    public NeighborhoodSnapshot(Chunk center, Chunk nx, Chunk px, Chunk nz, Chunk pz) {
        this.center = center;
        this.nx = nx;
        this.px = px;
        this.nz = nz;
        this.pz = pz;
        
        this.cx = center.getPos().x();
        this.cz = center.getPos().z();
        
        // Cache direct array references
        this.centerBlocks = center.getBlocksArray();
        this.nxBlocks = nx != null ? nx.getBlocksArray() : null;
        this.pxBlocks = px != null ? px.getBlocksArray() : null;
        this.nzBlocks = nz != null ? nz.getBlocksArray() : null;
        this.pzBlocks = pz != null ? pz.getBlocksArray() : null;
    }
    
    /**
     * Get block at local coordinates within this neighborhood.
     * No map lookups, pure array indexing.
     * 
     * @param lx local x (0-15 for center, can be -1 to 16 for neighbors)
     * @param y world y (0-127)
     * @param lz local z (0-15 for center, can be -1 to 16 for neighbors)
     * @return block ID, or 0 if out of bounds or neighbor unloaded
     */
    public int getBlock(int lx, int y, int lz) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return 0;

        // Handle diagonal corners - we don't have diagonal neighbor chunks
        // Return 0 (air) for any access that would require a diagonal chunk
        boolean needsXNeighbor = (lx < 0 || lx >= WorldConstants.CHUNK_SIZE);
        boolean needsZNeighbor = (lz < 0 || lz >= WorldConstants.CHUNK_SIZE);
        if (needsXNeighbor && needsZNeighbor) {
            return 0; // Diagonal access - no chunk data available
        }

        // Determine which chunk and adjust coords
        byte[] blocks;
        int adjX = lx;
        int adjZ = lz;

        if (lx < 0) {
            blocks = nxBlocks;
            adjX = lx + WorldConstants.CHUNK_SIZE;
        } else if (lx >= WorldConstants.CHUNK_SIZE) {
            blocks = pxBlocks;
            adjX = lx - WorldConstants.CHUNK_SIZE;
        } else if (lz < 0) {
            blocks = nzBlocks;
            adjZ = lz + WorldConstants.CHUNK_SIZE;
        } else if (lz >= WorldConstants.CHUNK_SIZE) {
            blocks = pzBlocks;
            adjZ = lz - WorldConstants.CHUNK_SIZE;
        } else {
            blocks = centerBlocks;
        }

        if (blocks == null) return 0;

        int idx = adjX + adjZ * WorldConstants.CHUNK_SIZE + y * WorldConstants.CHUNK_SIZE * WorldConstants.CHUNK_SIZE;
        return blocks[idx] & 0xFF;
    }
    
    /**
     * Get block at world coordinates.
     * Converts to local coords and delegates to getBlock(lx, y, lz).
     */
    public int getBlockWorld(int wx, int wy, int wz) {
        int lx = wx - cx * WorldConstants.CHUNK_SIZE;
        int lz = wz - cz * WorldConstants.CHUNK_SIZE;
        return getBlock(lx, wy, lz);
    }
    
    /**
     * Get sky visibility at local coords (requires center chunk only).
     */
    public float getSkyVisibility(int lx, int y, int lz) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return 1.0f;
        if (lx < 0 || lx >= WorldConstants.CHUNK_SIZE || lz < 0 || lz >= WorldConstants.CHUNK_SIZE) {
            return 1.0f; // Default for cross-chunk visibility
        }
        return center.getSkyVisibility(lx, y, lz);
    }
    
    /**
     * Get block light RGB at local coords.
     */
    public float[] getBlockLightRGB(int lx, int y, int lz) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return new float[]{0, 0, 0};

        // Handle diagonal corners - return black (no light data)
        boolean needsXNeighbor = (lx < 0 || lx >= WorldConstants.CHUNK_SIZE);
        boolean needsZNeighbor = (lz < 0 || lz >= WorldConstants.CHUNK_SIZE);
        if (needsXNeighbor && needsZNeighbor) {
            return new float[]{0, 0, 0};
        }

        Chunk chunk;
        int adjX = lx, adjZ = lz;

        if (lx < 0) {
            chunk = nx;
            adjX = lx + WorldConstants.CHUNK_SIZE;
        } else if (lx >= WorldConstants.CHUNK_SIZE) {
            chunk = px;
            adjX = lx - WorldConstants.CHUNK_SIZE;
        } else if (lz < 0) {
            chunk = nz;
            adjZ = lz + WorldConstants.CHUNK_SIZE;
        } else if (lz >= WorldConstants.CHUNK_SIZE) {
            chunk = pz;
            adjZ = lz - WorldConstants.CHUNK_SIZE;
        } else {
            chunk = center;
        }

        if (chunk == null) return new float[]{0, 0, 0};
        return chunk.getBlockLightRGB(adjX, y, adjZ);
    }
}
