package com.voxelgame.world;

import com.voxelgame.world.lod.LODLevel;
import com.voxelgame.world.mesh.ChunkMesh;

/**
 * A 16×128×16 column of blocks with light data.
 * Each position stores a block ID and packed light (high nibble = sky, low nibble = block).
 *
 * Supports multi-tier LOD meshes: each LOD level can have its own mesh.
 */
public class Chunk {

    private final ChunkPos pos;
    private final byte[] blocks;
    /** Packed light: high nibble (bits 4-7) = sky light, low nibble (bits 0-3) = block light. */
    private final byte[] lightMap;
    private boolean dirty = true;
    private boolean lightDirty = true;
    private ChunkMesh mesh;
    private ChunkMesh transparentMesh;

    // ---- LOD mesh storage ----
    /** LOD meshes indexed by LOD level (0-3). LOD 0 uses the main mesh/transparentMesh. */
    private final ChunkMesh[] lodMeshes = new ChunkMesh[4];
    /** Current assigned LOD level for rendering. */
    private volatile LODLevel currentLOD = LODLevel.LOD_0;
    /** Whether this chunk has a valid LOD mesh for its current level. */
    private volatile boolean lodMeshReady = false;

    /**
     * Whether this chunk has been modified by the player (block placed/removed)
     * since the last save. Used by the save system to know which chunks need persisting.
     * Separate from {@link #dirty} which tracks mesh rebuild need.
     */
    private volatile boolean modified = false;

    public Chunk(ChunkPos pos) {
        this.pos = pos;
        this.blocks = new byte[WorldConstants.CHUNK_VOLUME];
        this.lightMap = new byte[WorldConstants.CHUNK_VOLUME];
    }

    private int index(int x, int y, int z) {
        return (y * WorldConstants.CHUNK_SIZE * WorldConstants.CHUNK_SIZE) + (z * WorldConstants.CHUNK_SIZE) + x;
    }

    public int getBlock(int x, int y, int z) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return 0; // AIR for out of bounds
        }
        return blocks[index(x, y, z)] & 0xFF;
    }

    public void setBlock(int x, int y, int z, int blockId) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return;
        }
        blocks[index(x, y, z)] = (byte) blockId;
        dirty = true;
        modified = true;
    }

    /** Get sky light level (0-15) at local coordinates. */
    public int getSkyLight(int x, int y, int z) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return (y >= WorldConstants.WORLD_HEIGHT) ? 15 : 0;
        }
        return (lightMap[index(x, y, z)] >> 4) & 0xF;
    }

    /** Set sky light level (0-15) at local coordinates. */
    public void setSkyLight(int x, int y, int z, int level) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return;
        }
        int idx = index(x, y, z);
        lightMap[idx] = (byte) ((level << 4) | (lightMap[idx] & 0xF));
    }

    /** Get block light level (0-15) at local coordinates. */
    public int getBlockLight(int x, int y, int z) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return 0;
        }
        return lightMap[index(x, y, z)] & 0xF;
    }

    /** Set block light level (0-15) at local coordinates. */
    public void setBlockLight(int x, int y, int z, int level) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return;
        }
        int idx = index(x, y, z);
        lightMap[idx] = (byte) ((lightMap[idx] & 0xF0) | (level & 0xF));
    }

    public boolean isDirty() { return dirty; }
    public void setDirty(boolean d) { this.dirty = d; }
    public boolean isLightDirty() { return lightDirty; }
    public void setLightDirty(boolean d) { this.lightDirty = d; }
    public ChunkPos getPos() { return pos; }

    // --- Modified flag for save system ---

    /** Whether block data has been modified since last save. */
    public boolean isModified() { return modified; }
    public void setModified(boolean m) { this.modified = m; }

    // --- Raw array access for serialization (copy-on-read for thread safety) ---

    /**
     * Returns a snapshot copy of the block data array.
     * Thread-safe: caller gets an independent copy.
     */
    public byte[] snapshotBlocks() {
        byte[] copy = new byte[blocks.length];
        System.arraycopy(blocks, 0, copy, 0, blocks.length);
        return copy;
    }

    /**
     * Returns a snapshot copy of the light map array.
     * Thread-safe: caller gets an independent copy.
     */
    public byte[] snapshotLightMap() {
        byte[] copy = new byte[lightMap.length];
        System.arraycopy(lightMap, 0, copy, 0, lightMap.length);
        return copy;
    }

    /**
     * Bulk-load block data from a saved byte array.
     * Used when loading a chunk from disk.
     */
    public void loadBlocks(byte[] data) {
        int len = Math.min(data.length, blocks.length);
        System.arraycopy(data, 0, blocks, 0, len);
        dirty = true;
    }

    /**
     * Bulk-load light map data from a saved byte array.
     */
    public void loadLightMap(byte[] data) {
        int len = Math.min(data.length, lightMap.length);
        System.arraycopy(data, 0, lightMap, 0, len);
        lightDirty = true;
    }

    public ChunkMesh getMesh() { return mesh; }
    public void setMesh(ChunkMesh mesh) {
        if (this.mesh != null) {
            this.mesh.dispose();
        }
        this.mesh = mesh;
    }

    public ChunkMesh getTransparentMesh() { return transparentMesh; }
    public void setTransparentMesh(ChunkMesh mesh) {
        if (this.transparentMesh != null) {
            this.transparentMesh.dispose();
        }
        this.transparentMesh = mesh;
    }

    // ---- LOD mesh management ----

    /** Get the current LOD level assigned to this chunk. */
    public LODLevel getCurrentLOD() { return currentLOD; }

    /** Set the LOD level for this chunk. */
    public void setCurrentLOD(LODLevel lod) { this.currentLOD = lod; }

    /** Whether this chunk has a ready LOD mesh for its current level. */
    public boolean isLodMeshReady() { return lodMeshReady; }
    public void setLodMeshReady(boolean ready) { this.lodMeshReady = ready; }

    /** Get the LOD mesh for a specific level. Returns null if not generated. */
    public ChunkMesh getLodMesh(int level) {
        if (level < 0 || level >= lodMeshes.length) return null;
        return lodMeshes[level];
    }

    /** Set the LOD mesh for a specific level. Disposes old mesh if present. */
    public void setLodMesh(int level, ChunkMesh mesh) {
        if (level < 0 || level >= lodMeshes.length) return;
        if (lodMeshes[level] != null) {
            lodMeshes[level].dispose();
        }
        lodMeshes[level] = mesh;
    }

    /**
     * Get the best available mesh for rendering based on current LOD.
     * Uses a tiered fallback strategy:
     *   1. Current LOD mesh or higher LOD number (lower detail)
     *   2. Lower LOD number (higher detail, but still simplified)
     *   3. Full mesh only for LOD 0-1 (close enough to look acceptable)
     *   4. null for LOD 2+ (too far — don't show full-detail mesh at distance)
     */
    public ChunkMesh getRenderMesh() {
        int level = currentLOD.level();
        // LOD 0 uses the main opaque mesh
        if (level == 0) return mesh;
        // Check current LOD and higher LOD numbers (lower detail)
        for (int i = level; i <= 3; i++) {
            if (lodMeshes[i] != null && !lodMeshes[i].isEmpty()) {
                return lodMeshes[i];
            }
        }
        // Check lower LOD numbers (higher detail, but still simplified)
        for (int i = level - 1; i >= 1; i--) {
            if (lodMeshes[i] != null && !lodMeshes[i].isEmpty()) {
                return lodMeshes[i];
            }
        }
        // For LOD 1, full mesh is acceptable (close distance)
        if (level <= 1) return mesh;
        // For LOD 2+, don't render full-detail mesh at distance
        return null;
    }

    /**
     * Get transparent mesh only for LOD 0 (close chunks).
     * LOD 1+ don't render transparent geometry.
     */
    public ChunkMesh getRenderTransparentMesh() {
        if (currentLOD == LODLevel.LOD_0) return transparentMesh;
        return null;
    }

    public void dispose() {
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
        if (transparentMesh != null) {
            transparentMesh.dispose();
            transparentMesh = null;
        }
        for (int i = 0; i < lodMeshes.length; i++) {
            if (lodMeshes[i] != null) {
                lodMeshes[i].dispose();
                lodMeshes[i] = null;
            }
        }
    }

    /** Fill with flat world terrain for testing. */
    public void generateFlat() {
        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                for (int y = 0; y < WorldConstants.WORLD_HEIGHT; y++) {
                    int blockId;
                    if (y < 60) {
                        blockId = 1; // STONE
                    } else if (y < 64) {
                        blockId = 3; // DIRT
                    } else if (y == 64) {
                        blockId = 4; // GRASS
                    } else {
                        blockId = 0; // AIR
                    }
                    blocks[index(x, y, z)] = (byte) blockId;
                }
            }
        }
        dirty = true;
    }
}
