package com.voxelgame.world;

import com.voxelgame.world.lod.LODLevel;
import com.voxelgame.world.mesh.ChunkMesh;

/**
 * A 16×128×16 column of blocks with light data.
 * Each position stores a block ID and packed light (high nibble = sky, low nibble = block).
 *
 * Phase 4: RGB block light stored separately for colored light sources.
 * Supports multi-tier LOD meshes: each LOD level can have its own mesh.
 */
public class Chunk {

    private final ChunkPos pos;
    private final byte[] blocks;
    /** Packed light: high nibble (bits 4-7) = sky light, low nibble (bits 0-3) = legacy block light. */
    private final byte[] lightMap;
    
    // Phase 4: RGB block light storage (0-255 per channel, normalized to 0-1 in accessors)
    private final byte[] blockLightR;
    private final byte[] blockLightG;
    private final byte[] blockLightB;
    private boolean dirty = true;
    private boolean lightDirty = true;

    // ---- Heightmap cache for fast visibility calculations ----
    /**
     * Cached max solid block height for each X,Z column.
     * heightmap[x * CHUNK_SIZE + z] = highest Y with solid block, or -1 if empty.
     * Invalidated when blocks change, lazily recomputed on first access.
     */
    private short[] heightmap;
    private volatile boolean heightmapDirty = true;

    // ---- Mesh storage (VOLATILE for thread safety between upload/render threads) ----
    private volatile ChunkMesh mesh;
    private volatile ChunkMesh transparentMesh;
    private final Object meshLock = new Object();

    // ---- LOD mesh storage ----
    /** LOD meshes indexed by LOD level (0-3). LOD 0 uses the main mesh/transparentMesh. */
    private final ChunkMesh[] lodMeshes = new ChunkMesh[4];
    /** Current assigned LOD level for rendering. */
    private volatile LODLevel currentLOD = LODLevel.LOD_0;
    /** Whether this chunk has a valid LOD mesh for its current level. */
    private volatile boolean lodMeshReady = false;

    // ---- Section-based rendering (8 vertical sections of 16×16×16) ----
    /**
     * Section state flags. Each section (16×16×16) can be:
     * - EMPTY (0): all air, no mesh needed
     * - MIXED (1): has both air and solid blocks, needs full meshing
     * - SOLID (2): all solid opaque blocks, only boundary faces needed
     */
    public static final byte SECTION_EMPTY = 0;
    public static final byte SECTION_MIXED = 1;
    public static final byte SECTION_SOLID = 2;

    private final byte[] sectionFlags = new byte[WorldConstants.SECTIONS_PER_CHUNK];
    private volatile boolean sectionFlagsDirty = true;

    /** Per-section opaque meshes. Null if section is empty or not yet meshed. */
    private final ChunkMesh[] sectionMeshes = new ChunkMesh[WorldConstants.SECTIONS_PER_CHUNK];
    /** Per-section transparent meshes. Null if section has no transparent blocks. */
    private final ChunkMesh[] sectionTransparentMeshes = new ChunkMesh[WorldConstants.SECTIONS_PER_CHUNK];
    private final Object sectionMeshLock = new Object();

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
        // Phase 4: RGB block light
        this.blockLightR = new byte[WorldConstants.CHUNK_VOLUME];
        this.blockLightG = new byte[WorldConstants.CHUNK_VOLUME];
        this.blockLightB = new byte[WorldConstants.CHUNK_VOLUME];
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
        heightmapDirty = true; // Invalidate heightmap cache
        sectionFlagsDirty = true; // Invalidate section flags
    }

    /** 
     * Get sky visibility (0.0 - 1.0) at local coordinates.
     * Returns 1.0 if the block has an unobstructed column to the sky, 0.0 otherwise.
     * Stored internally as 0-15 in the high nibble of lightMap.
     */
    public float getSkyVisibility(int x, int y, int z) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return (y >= WorldConstants.WORLD_HEIGHT) ? 1.0f : 0.0f;
        }
        int raw = (lightMap[index(x, y, z)] >> 4) & 0xF;
        return raw / 15.0f;
    }

    /**
     * Set sky visibility (0.0 - 1.0) at local coordinates.
     * Stored internally as 0-15 in the high nibble of lightMap.
     */
    public void setSkyVisibility(int x, int y, int z, float visibility) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return;
        }
        int level = Math.clamp(Math.round(visibility * 15.0f), 0, 15);
        int idx = index(x, y, z);
        lightMap[idx] = (byte) ((level << 4) | (lightMap[idx] & 0xF));
    }

    /** 
     * Get sky light level (0-15) at local coordinates.
     * @deprecated Use {@link #getSkyVisibility(int, int, int)} for the new lighting model.
     * This method is kept for backward compatibility during transition.
     */
    @Deprecated
    public int getSkyLight(int x, int y, int z) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return (y >= WorldConstants.WORLD_HEIGHT) ? 15 : 0;
        }
        return (lightMap[index(x, y, z)] >> 4) & 0xF;
    }

    /** 
     * Set sky light level (0-15) at local coordinates.
     * @deprecated Use {@link #setSkyVisibility(int, int, int, float)} for the new lighting model.
     * This method is kept for backward compatibility during transition.
     */
    @Deprecated
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

    // ========================================================================
    // Phase 4: RGB Block Light Methods
    // ========================================================================

    /**
     * Get RGB block light at local coordinates.
     * Returns a 3-element float array [R, G, B] normalized to 0-1.
     */
    public float[] getBlockLightRGB(int x, int y, int z) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return new float[] {0, 0, 0};
        }
        int idx = index(x, y, z);
        return new float[] {
            (blockLightR[idx] & 0xFF) / 255.0f,
            (blockLightG[idx] & 0xFF) / 255.0f,
            (blockLightB[idx] & 0xFF) / 255.0f
        };
    }

    /**
     * Get individual RGB block light channels at local coordinates.
     * @return value 0-1
     */
    public float getBlockLightR(int x, int y, int z) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return 0;
        }
        return (blockLightR[index(x, y, z)] & 0xFF) / 255.0f;
    }

    public float getBlockLightG(int x, int y, int z) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return 0;
        }
        return (blockLightG[index(x, y, z)] & 0xFF) / 255.0f;
    }

    public float getBlockLightB(int x, int y, int z) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return 0;
        }
        return (blockLightB[index(x, y, z)] & 0xFF) / 255.0f;
    }

    /**
     * Set RGB block light at local coordinates.
     * @param r red 0-1
     * @param g green 0-1
     * @param b blue 0-1
     */
    public void setBlockLightRGB(int x, int y, int z, float r, float g, float b) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return;
        }
        int idx = index(x, y, z);
        blockLightR[idx] = (byte) Math.clamp(Math.round(r * 255), 0, 255);
        blockLightG[idx] = (byte) Math.clamp(Math.round(g * 255), 0, 255);
        blockLightB[idx] = (byte) Math.clamp(Math.round(b * 255), 0, 255);
        
        // Also update legacy scalar block light (max channel for compatibility)
        int maxChannel = Math.max(Math.max(blockLightR[idx] & 0xFF, blockLightG[idx] & 0xFF), 
                                  blockLightB[idx] & 0xFF);
        int legacyLevel = maxChannel * 15 / 255;
        lightMap[idx] = (byte) ((lightMap[idx] & 0xF0) | (legacyLevel & 0xF));
    }

    /**
     * Clear all RGB block light in this chunk.
     */
    public void clearBlockLightRGB() {
        java.util.Arrays.fill(blockLightR, (byte) 0);
        java.util.Arrays.fill(blockLightG, (byte) 0);
        java.util.Arrays.fill(blockLightB, (byte) 0);
    }

    public boolean isDirty() { return dirty; }
    public void setDirty(boolean d) { this.dirty = d; }
    public boolean isLightDirty() { return lightDirty; }
    public void setLightDirty(boolean d) { this.lightDirty = d; }
    public ChunkPos getPos() { return pos; }

    // ========================================================================
    // Heightmap Cache for Fast Visibility Calculations
    // ========================================================================

    /**
     * Get the highest solid block Y coordinate for a column.
     * Uses cached heightmap for O(1) lookups after initial computation.
     *
     * @param x local X (0-15)
     * @param z local Z (0-15)
     * @return highest Y with solid block, or -1 if column is empty
     */
    public int getHeightAt(int x, int z) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return -1;
        }
        ensureHeightmap();
        return heightmap[x * WorldConstants.CHUNK_SIZE + z];
    }

    /**
     * Check if a position has clear sky access (no solid blocks above).
     * Uses cached heightmap for O(1) lookup.
     *
     * @return true if position Y is at or above the highest solid block
     */
    public boolean hasSkyAccess(int x, int y, int z) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return true; // Out of bounds treated as sky access
        }
        if (y >= WorldConstants.WORLD_HEIGHT) return true;
        ensureHeightmap();
        return y > heightmap[x * WorldConstants.CHUNK_SIZE + z];
    }

    /**
     * Ensure heightmap is computed. Called lazily on first access.
     * Thread-safe via double-checked locking pattern.
     */
    private void ensureHeightmap() {
        if (!heightmapDirty && heightmap != null) return;

        synchronized (this) {
            if (!heightmapDirty && heightmap != null) return;

            if (heightmap == null) {
                heightmap = new short[WorldConstants.CHUNK_SIZE * WorldConstants.CHUNK_SIZE];
            }

            // Compute max height for each column
            for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
                for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                    int idx = x * WorldConstants.CHUNK_SIZE + z;
                    heightmap[idx] = -1;

                    for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
                        int blockId = blocks[index(x, y, z)] & 0xFF;
                        if (blockId != 0) {
                            Block block = Blocks.get(blockId);
                            if (block.solid() && !block.transparent()) {
                                heightmap[idx] = (short) y;
                                break;
                            }
                        }
                    }
                }
            }

            heightmapDirty = false;
        }
    }

    /**
     * Invalidate heightmap cache. Called when blocks are modified.
     */
    public void invalidateHeightmap() {
        heightmapDirty = true;
    }

    /**
     * Get the entire heightmap array for batch processing.
     * Returns null if not yet computed.
     */
    public short[] getHeightmapSnapshot() {
        ensureHeightmap();
        short[] copy = new short[heightmap.length];
        System.arraycopy(heightmap, 0, copy, 0, heightmap.length);
        return copy;
    }
    
    /** Direct access to block array for zero-overhead reads in meshing snapshot. */
    public byte[] getBlocksArray() { return blocks; }

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

    /**
     * Set the opaque mesh. Thread-safe via synchronized block.
     * Disposes old mesh if present.
     */
    public void setMesh(ChunkMesh newMesh) {
        synchronized (meshLock) {
            if (this.mesh != null) {
                this.mesh.dispose();
            }
            this.mesh = newMesh;
        }
    }

    public ChunkMesh getTransparentMesh() { return transparentMesh; }

    /**
     * Set the transparent mesh. Thread-safe via synchronized block.
     * Disposes old mesh if present.
     */
    public void setTransparentMesh(ChunkMesh newMesh) {
        synchronized (meshLock) {
            if (this.transparentMesh != null) {
                this.transparentMesh.dispose();
            }
            this.transparentMesh = newMesh;
        }
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

    // ========================================================================
    // Section-Based Rendering Methods
    // ========================================================================

    /**
     * Get the section index (0-7) for a given Y coordinate.
     */
    public static int getSectionIndex(int y) {
        return y / WorldConstants.SECTION_HEIGHT;
    }

    /**
     * Get the section flag for a given section index.
     * Returns SECTION_EMPTY, SECTION_MIXED, or SECTION_SOLID.
     */
    public byte getSectionFlag(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= WorldConstants.SECTIONS_PER_CHUNK) {
            return SECTION_EMPTY;
        }
        ensureSectionFlags();
        return sectionFlags[sectionIndex];
    }

    /**
     * Check if a section needs meshing (is MIXED or SOLID with boundary faces).
     */
    public boolean sectionNeedsMesh(int sectionIndex) {
        byte flag = getSectionFlag(sectionIndex);
        return flag != SECTION_EMPTY;
    }

    /**
     * Compute section flags for all 8 sections.
     * Called lazily on first access or when blocks change.
     */
    private void ensureSectionFlags() {
        if (!sectionFlagsDirty) return;

        synchronized (this) {
            if (!sectionFlagsDirty) return;

            for (int section = 0; section < WorldConstants.SECTIONS_PER_CHUNK; section++) {
                int yStart = section * WorldConstants.SECTION_HEIGHT;
                int yEnd = yStart + WorldConstants.SECTION_HEIGHT;

                boolean hasAir = false;
                boolean hasSolid = false;
                boolean hasTransparent = false;

                outer:
                for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
                    for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                        for (int y = yStart; y < yEnd; y++) {
                            int blockId = blocks[index(x, y, z)] & 0xFF;
                            if (blockId == 0) {
                                hasAir = true;
                            } else {
                                Block block = Blocks.get(blockId);
                                if (block.transparent()) {
                                    hasTransparent = true;
                                    hasAir = true; // Transparent counts as "not fully solid"
                                }
                                if (block.solid() && !block.transparent()) {
                                    hasSolid = true;
                                }
                            }
                            // Early exit if we know it's mixed
                            if ((hasAir || hasTransparent) && hasSolid) {
                                sectionFlags[section] = SECTION_MIXED;
                                continue outer;
                            }
                        }
                    }
                }

                // Determine final flag
                if (!hasSolid && !hasTransparent) {
                    sectionFlags[section] = SECTION_EMPTY;
                } else if (!hasAir && hasSolid && !hasTransparent) {
                    sectionFlags[section] = SECTION_SOLID;
                } else {
                    sectionFlags[section] = SECTION_MIXED;
                }
            }

            sectionFlagsDirty = false;
        }
    }

    /**
     * Invalidate section flags. Called when blocks change.
     */
    public void invalidateSectionFlags() {
        sectionFlagsDirty = true;
    }

    /**
     * Get the opaque mesh for a section.
     */
    public ChunkMesh getSectionMesh(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= WorldConstants.SECTIONS_PER_CHUNK) {
            return null;
        }
        return sectionMeshes[sectionIndex];
    }

    /**
     * Set the opaque mesh for a section.
     */
    public void setSectionMesh(int sectionIndex, ChunkMesh mesh) {
        if (sectionIndex < 0 || sectionIndex >= WorldConstants.SECTIONS_PER_CHUNK) {
            return;
        }
        synchronized (sectionMeshLock) {
            if (sectionMeshes[sectionIndex] != null) {
                sectionMeshes[sectionIndex].dispose();
            }
            sectionMeshes[sectionIndex] = mesh;
        }
    }

    /**
     * Get the transparent mesh for a section.
     */
    public ChunkMesh getSectionTransparentMesh(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= WorldConstants.SECTIONS_PER_CHUNK) {
            return null;
        }
        return sectionTransparentMeshes[sectionIndex];
    }

    /**
     * Set the transparent mesh for a section.
     */
    public void setSectionTransparentMesh(int sectionIndex, ChunkMesh mesh) {
        if (sectionIndex < 0 || sectionIndex >= WorldConstants.SECTIONS_PER_CHUNK) {
            return;
        }
        synchronized (sectionMeshLock) {
            if (sectionTransparentMeshes[sectionIndex] != null) {
                sectionTransparentMeshes[sectionIndex].dispose();
            }
            sectionTransparentMeshes[sectionIndex] = mesh;
        }
    }

    /**
     * Check if any section has a mesh (for compatibility with existing code).
     */
    public boolean hasAnySectionMesh() {
        for (int i = 0; i < WorldConstants.SECTIONS_PER_CHUNK; i++) {
            if (sectionMeshes[i] != null || sectionTransparentMeshes[i] != null) {
                return true;
            }
        }
        return false;
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
        // Dispose section meshes
        for (int i = 0; i < WorldConstants.SECTIONS_PER_CHUNK; i++) {
            if (sectionMeshes[i] != null) {
                sectionMeshes[i].dispose();
                sectionMeshes[i] = null;
            }
            if (sectionTransparentMeshes[i] != null) {
                sectionTransparentMeshes[i].dispose();
                sectionTransparentMeshes[i] = null;
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
