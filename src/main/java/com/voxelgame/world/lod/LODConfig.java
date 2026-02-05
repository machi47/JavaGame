package com.voxelgame.world.lod;

/**
 * Configuration for the LOD system. Controls distance thresholds,
 * quality presets, and performance tuning parameters.
 *
 * Thread-safe: volatile fields for live settings changes.
 */
public class LODConfig {

    // ---- Distance thresholds (in chunks) ----

    /** Distance where LOD 1 starts (full-detail radius). Increased for earlier transitions. */
    private volatile int lodThreshold = 12;

    /** Distance where LOD 2 starts. Computed from threshold. */
    private volatile int lod2Start = 16;

    /** Distance where LOD 3 starts. Computed from threshold. */
    private volatile int lod3Start = 20;

    /** Maximum render distance (LOD 3 extends to this). Default: 20 */
    private volatile int maxRenderDistance = 20;

    /** Unload distance — chunks beyond this are removed. */
    private volatile int unloadDistance = 22;

    /**
     * Hard cap on total loaded chunks to prevent memory/perf overload.
     * Uses circular area (π*r²) + margin, NOT square area.
     * Recalculated when maxRenderDistance changes.
     */
    private volatile int maxLoadedChunks = 1300;

    /**
     * Absolute ceiling on loaded chunks regardless of render distance.
     * Prevents catastrophic performance collapse even with max settings.
     */
    public static final int ABSOLUTE_MAX_CHUNKS = 2500;

    // ---- Performance tuning ----

    /** Max chunk generations per frame for LOD 0-1 (close chunks). */
    public static final int MAX_CLOSE_GEN_PER_FRAME = 4;

    /** Max chunk generations per frame for LOD 2-3 (distant chunks). */
    public static final int MAX_FAR_GEN_PER_FRAME = 6;

    /** Max mesh uploads per frame (GPU operations). Increased for faster loading. */
    public static final int MAX_MESH_UPLOADS_PER_FRAME = 12;

    /** Max LOD mesh uploads per frame (separate budget for distant). */
    public static final int MAX_LOD_UPLOADS_PER_FRAME = 16;

    /** Generation thread pool size. */
    public static final int GEN_THREAD_COUNT = 4;

    /** Mesh building thread pool size. */
    public static final int MESH_THREAD_COUNT = 3;

    // ---- Quality presets ----

    public enum Quality {
        LOW(4, 12),
        MEDIUM(8, 20),
        HIGH(8, 32),
        ULTRA(10, 40);

        public final int threshold;
        public final int maxDistance;

        Quality(int threshold, int maxDistance) {
            this.threshold = threshold;
            this.maxDistance = maxDistance;
        }
    }

    public LODConfig() {
        recalcBoundaries();
    }

    // ---- Getters ----

    public int getLodThreshold() { return lodThreshold; }
    public int getLod2Start() { return lod2Start; }
    public int getLod3Start() { return lod3Start; }
    public int getMaxRenderDistance() { return maxRenderDistance; }
    public int getUnloadDistance() { return unloadDistance; }
    public int getMaxLoadedChunks() { return maxLoadedChunks; }

    // ---- Setters ----

    public void setLodThreshold(int threshold) {
        this.lodThreshold = Math.max(2, Math.min(16, threshold));
        recalcBoundaries();
    }

    public void setMaxRenderDistance(int distance) {
        this.maxRenderDistance = Math.max(8, Math.min(40, distance));
        recalcBoundaries();
    }

    public void applyPreset(Quality quality) {
        this.lodThreshold = quality.threshold;
        this.maxRenderDistance = quality.maxDistance;
        recalcBoundaries();
    }

    /**
     * Recalculate LOD boundaries based on threshold and max distance.
     * Divides the range between threshold and max into 3 zones.
     */
    private void recalcBoundaries() {
        int range = maxRenderDistance - lodThreshold;
        // LOD 1: threshold to threshold + range*0.3
        // LOD 2: threshold + range*0.3 to threshold + range*0.6
        // LOD 3: threshold + range*0.6 to maxRenderDistance
        this.lod2Start = lodThreshold + (int)(range * 0.3);
        this.lod3Start = lodThreshold + (int)(range * 0.6);
        this.unloadDistance = maxRenderDistance + 2;
        // Use circular area (π*r²) with 10% margin, NOT square area.
        // For r=20: π*400 ≈ 1,257. For r=40: π*1600 ≈ 5,027.
        int circularArea = (int)(Math.PI * maxRenderDistance * maxRenderDistance * 1.1);
        this.maxLoadedChunks = Math.min(circularArea, ABSOLUTE_MAX_CHUNKS);
    }

    /**
     * Determine the LOD level for a chunk at the given distance (in chunks).
     */
    public LODLevel getLevelForDistance(int distSq) {
        // Use squared distances for efficiency
        if (distSq <= lodThreshold * lodThreshold) return LODLevel.LOD_0;
        if (distSq <= lod2Start * lod2Start) return LODLevel.LOD_1;
        if (distSq <= lod3Start * lod3Start) return LODLevel.LOD_2;
        return LODLevel.LOD_3;
    }

    /**
     * Get the fog start distance in world units for the shader.
     */
    public float getFogStart() {
        return (maxRenderDistance - 8) * 16.0f;
    }

    /**
     * Get the fog end distance in world units for the shader.
     */
    public float getFogEnd() {
        return maxRenderDistance * 16.0f;
    }

    /**
     * Get the camera far plane distance.
     */
    public float getFarPlane() {
        return (maxRenderDistance + 4) * 16.0f;
    }
}
