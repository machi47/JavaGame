package com.voxelgame.world.mesh;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldAccess;
import com.voxelgame.world.WorldConstants;

/**
 * WorldAccess implementation backed by a NeighborhoodSnapshot.
 * Provides the same interface as World but with zero map lookups.
 * All reads are direct array access through the pre-resolved snapshot.
 */
public final class SnapshotWorldAccess implements WorldAccess {
    
    private final NeighborhoodSnapshot snap;
    private final int worldX, worldZ; // World offset for coordinate conversion
    
    public SnapshotWorldAccess(NeighborhoodSnapshot snap) {
        this.snap = snap;
        this.worldX = snap.cx * WorldConstants.CHUNK_SIZE;
        this.worldZ = snap.cz * WorldConstants.CHUNK_SIZE;
    }
    
    @Override
    public int getBlock(int x, int y, int z) {
        int lx = x - worldX;
        int lz = z - worldZ;
        return snap.getBlock(lx, y, lz);
    }
    
    @Override
    public float getSkyVisibility(int x, int y, int z) {
        int lx = x - worldX;
        int lz = z - worldZ;
        return snap.getSkyVisibility(lx, y, lz);
    }
    
    @Override
    @Deprecated
    public int getSkyLight(int x, int y, int z) {
        // Approximate from visibility
        return (int)(snap.getSkyVisibility(x - worldX, y, z - worldZ) * 15);
    }
    
    @Override
    public int getBlockLight(int x, int y, int z) {
        float[] rgb = snap.getBlockLightRGB(x - worldX, y, z - worldZ);
        return (int)(Math.max(rgb[0], Math.max(rgb[1], rgb[2])) * 15);
    }
    
    @Override
    public float[] getBlockLightRGB(int x, int y, int z) {
        return snap.getBlockLightRGB(x - worldX, y, z - worldZ);
    }
    
    @Override
    public float getBlockLightR(int x, int y, int z) {
        return snap.getBlockLightRGB(x - worldX, y, z - worldZ)[0];
    }
    
    @Override
    public float getBlockLightG(int x, int y, int z) {
        return snap.getBlockLightRGB(x - worldX, y, z - worldZ)[1];
    }
    
    @Override
    public float getBlockLightB(int x, int y, int z) {
        return snap.getBlockLightRGB(x - worldX, y, z - worldZ)[2];
    }
    
    @Override
    public Chunk getChunk(int cx, int cz) {
        // Return appropriate chunk from snapshot
        if (cx == snap.cx && cz == snap.cz) return snap.center;
        if (cx == snap.cx - 1 && cz == snap.cz) return snap.nx;
        if (cx == snap.cx + 1 && cz == snap.cz) return snap.px;
        if (cx == snap.cx && cz == snap.cz - 1) return snap.nz;
        if (cx == snap.cx && cz == snap.cz + 1) return snap.pz;
        return null;
    }
    
    @Override
    public boolean isLoaded(int cx, int cz) {
        return getChunk(cx, cz) != null;
    }
}
