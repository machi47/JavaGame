package com.voxelgame.world.lighting;

import com.voxelgame.render.SkySystem;
import com.voxelgame.world.*;

/**
 * Per-chunk irradiance probe grid for one-bounce indirect lighting.
 * 
 * Stores a 4×32×4 grid of RGB irradiance values per chunk.
 * - 4 probes across chunk X (spacing = 4 blocks)
 * - 32 probes vertically (y = 0 to 128, spacing = 4 blocks)
 * - 4 probes across chunk Z (spacing = 4 blocks)
 * 
 * Each probe is updated by voxel marching in multiple directions to
 * sample incoming light from sky and nearby surfaces. The bounce
 * term captures color bleed from sunlit surfaces into shadows.
 * 
 * At runtime, vertices trilinear-interpolate between the 8 nearest probes.
 */
public class IrradianceProbeGrid {

    /** Probe resolution: 4 probes across each horizontal chunk axis */
    private static final int PROBE_RES_XZ = 4;
    
    /** Probe resolution: 32 probes vertically (covers y = 0 to 128) */
    private static final int PROBE_RES_Y = 32;
    
    /** Spacing between probes in blocks */
    private static final int PROBE_SPACING = 4;
    
    /** Maximum voxel march distance (blocks) */
    private static final int MAX_MARCH_DIST = 16;
    
    /** Bounce light strength multiplier (keep subtle) */
    private static final float BOUNCE_STRENGTH = 0.15f;
    
    /** Number of sample directions (hemisphere sampling) */
    private static final int NUM_SAMPLES = 14;
    
    /** Sample directions - hemisphere + horizontal spread */
    private static final float[][] SAMPLE_DIRS = generateSampleDirections();
    
    // RGB irradiance storage
    private final float[][][] r;
    private final float[][][] g;
    private final float[][][] b;
    
    // Chunk position
    private final int chunkX;
    private final int chunkZ;
    
    // Dirty flag for incremental updates
    private boolean dirty = true;
    
    // Update priority (higher = update sooner)
    private int updatePriority = 0;
    
    /**
     * Generate sample directions for probe updates.
     * Uses a distribution that samples upward hemisphere + horizontal spread.
     */
    private static float[][] generateSampleDirections() {
        // Precomputed hemisphere directions (normalized)
        // 6 cardinal + 8 diagonal/angled directions
        return new float[][] {
            // Cardinal directions (horizontal)
            { 1, 0, 0},
            {-1, 0, 0},
            { 0, 0, 1},
            { 0, 0,-1},
            // Upward
            { 0, 1, 0},
            // Diagonal up (45°)
            { 0.707f, 0.707f, 0},
            {-0.707f, 0.707f, 0},
            { 0, 0.707f, 0.707f},
            { 0, 0.707f,-0.707f},
            // Diagonal horizontal
            { 0.707f, 0, 0.707f},
            {-0.707f, 0, 0.707f},
            { 0.707f, 0,-0.707f},
            {-0.707f, 0,-0.707f},
            // Downward (for ceilings)
            { 0,-0.5f, 0},
        };
    }
    
    public IrradianceProbeGrid(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.r = new float[PROBE_RES_XZ][PROBE_RES_Y][PROBE_RES_XZ];
        this.g = new float[PROBE_RES_XZ][PROBE_RES_Y][PROBE_RES_XZ];
        this.b = new float[PROBE_RES_XZ][PROBE_RES_Y][PROBE_RES_XZ];
    }
    
    /**
     * Update all probes in this grid.
     * Call when chunk is first generated or time of day changes significantly.
     */
    public void updateAllProbes(WorldAccess world, SkySystem sky, float timeOfDay) {
        for (int px = 0; px < PROBE_RES_XZ; px++) {
            for (int py = 0; py < PROBE_RES_Y; py++) {
                for (int pz = 0; pz < PROBE_RES_XZ; pz++) {
                    updateSingleProbe(px, py, pz, world, sky, timeOfDay);
                }
            }
        }
        dirty = false;
    }
    
    /**
     * Update probes near a specific world position.
     * Call when a block is placed/removed.
     * 
     * @param worldX World X coordinate of change
     * @param worldY World Y coordinate of change
     * @param worldZ World Z coordinate of change
     */
    public void updateProbesNear(int worldX, int worldY, int worldZ, 
                                  WorldAccess world, SkySystem sky, float timeOfDay) {
        // Convert world coords to probe coords
        int baseWX = chunkX * 16;
        int baseWZ = chunkZ * 16;
        
        // Find the nearest probe
        int probeX = (worldX - baseWX) / PROBE_SPACING;
        int probeY = worldY / PROBE_SPACING;
        int probeZ = (worldZ - baseWZ) / PROBE_SPACING;
        
        // Update 3x3x3 neighborhood of probes
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int px = probeX + dx;
                    int py = probeY + dy;
                    int pz = probeZ + dz;
                    
                    if (px >= 0 && px < PROBE_RES_XZ &&
                        py >= 0 && py < PROBE_RES_Y &&
                        pz >= 0 && pz < PROBE_RES_XZ) {
                        updateSingleProbe(px, py, pz, world, sky, timeOfDay);
                    }
                }
            }
        }
    }
    
    /**
     * Update a single probe by voxel marching in all sample directions.
     */
    private void updateSingleProbe(int px, int py, int pz, 
                                    WorldAccess world, SkySystem sky, float timeOfDay) {
        // Probe world position (center of the probe cell)
        float worldX = chunkX * 16 + px * PROBE_SPACING + PROBE_SPACING / 2.0f;
        float worldY = py * PROBE_SPACING + PROBE_SPACING / 2.0f;
        float worldZ = chunkZ * 16 + pz * PROBE_SPACING + PROBE_SPACING / 2.0f;
        
        float irradianceR = 0;
        float irradianceG = 0;
        float irradianceB = 0;
        int sampleCount = 0;
        
        // Get sky colors for this time of day
        float[] zenithColor = sky.getZenithColor(timeOfDay);
        float[] horizonColor = sky.getHorizonColor(timeOfDay);
        float skyIntensity = sky.getSkyIntensity(timeOfDay);
        
        // Sample in each direction
        for (float[] dir : SAMPLE_DIRS) {
            MarchResult result = voxelMarch(worldX, worldY, worldZ, 
                                            dir[0], dir[1], dir[2], world);
            
            if (result.hitSky) {
                // This direction sees sky - add sky contribution
                // Blend zenith/horizon based on vertical angle
                float upness = Math.max(0, dir[1]); // 0 = horizontal, 1 = straight up
                float[] skyColor = new float[3];
                skyColor[0] = zenithColor[0] * upness + horizonColor[0] * (1 - upness);
                skyColor[1] = zenithColor[1] * upness + horizonColor[1] * (1 - upness);
                skyColor[2] = zenithColor[2] * upness + horizonColor[2] * (1 - upness);
                
                irradianceR += skyColor[0] * skyIntensity;
                irradianceG += skyColor[1] * skyIntensity;
                irradianceB += skyColor[2] * skyIntensity;
            } else if (result.hitBlock) {
                // Hit a block - compute bounce from that surface
                float[] surfaceLight = estimateSurfaceLight(
                    result.hitX, result.hitY, result.hitZ, 
                    world, sky, timeOfDay
                );
                float[] albedo = getBlockAlbedo(result.blockId);
                
                // Add bounce contribution (diffuse * albedo * bounce_strength)
                irradianceR += surfaceLight[0] * albedo[0] * BOUNCE_STRENGTH;
                irradianceG += surfaceLight[1] * albedo[1] * BOUNCE_STRENGTH;
                irradianceB += surfaceLight[2] * albedo[2] * BOUNCE_STRENGTH;
            }
            sampleCount++;
        }
        
        // Average the samples
        if (sampleCount > 0) {
            irradianceR /= sampleCount;
            irradianceG /= sampleCount;
            irradianceB /= sampleCount;
        }
        
        // Store in grid
        r[px][py][pz] = irradianceR;
        g[px][py][pz] = irradianceG;
        b[px][py][pz] = irradianceB;
    }
    
    /**
     * Voxel march from origin in direction, finding the first hit.
     */
    private MarchResult voxelMarch(float startX, float startY, float startZ,
                                    float dirX, float dirY, float dirZ,
                                    WorldAccess world) {
        // DDA-style voxel traversal
        float x = startX;
        float y = startY;
        float z = startZ;
        
        // Step sizes
        int stepX = dirX > 0 ? 1 : -1;
        int stepY = dirY > 0 ? 1 : -1;
        int stepZ = dirZ > 0 ? 1 : -1;
        
        // How far we need to travel to cross a voxel boundary
        float tDeltaX = Math.abs(dirX) > 0.0001f ? Math.abs(1.0f / dirX) : 9999f;
        float tDeltaY = Math.abs(dirY) > 0.0001f ? Math.abs(1.0f / dirY) : 9999f;
        float tDeltaZ = Math.abs(dirZ) > 0.0001f ? Math.abs(1.0f / dirZ) : 9999f;
        
        // Distance to next boundary
        int blockX = (int) Math.floor(x);
        int blockY = (int) Math.floor(y);
        int blockZ = (int) Math.floor(z);
        
        float tMaxX = tDeltaX * (dirX > 0 ? (blockX + 1 - x) : (x - blockX));
        float tMaxY = tDeltaY * (dirY > 0 ? (blockY + 1 - y) : (y - blockY));
        float tMaxZ = tDeltaZ * (dirZ > 0 ? (blockZ + 1 - z) : (z - blockZ));
        
        float traveled = 0;
        
        while (traveled < MAX_MARCH_DIST) {
            // Step to next voxel
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                traveled = tMaxX;
                tMaxX += tDeltaX;
                blockX += stepX;
            } else if (tMaxY < tMaxZ) {
                traveled = tMaxY;
                tMaxY += tDeltaY;
                blockY += stepY;
            } else {
                traveled = tMaxZ;
                tMaxZ += tDeltaZ;
                blockZ += stepZ;
            }
            
            // Check world bounds
            if (blockY < 0) {
                // Below world - consider it blocked
                return new MarchResult(false, false, 0, 0, 0, 0);
            }
            if (blockY >= WorldConstants.WORLD_HEIGHT) {
                // Above world - hit sky
                return new MarchResult(true, false, 0, 0, 0, 0);
            }
            
            // Check block at this position
            int blockId = world.getBlock(blockX, blockY, blockZ);
            if (blockId != 0) {
                Block block = Blocks.get(blockId);
                if (block.solid() && !block.transparent()) {
                    // Hit opaque block
                    return new MarchResult(false, true, blockX, blockY, blockZ, blockId);
                }
            }
        }
        
        // Reached max distance without hitting anything solid
        // Check if direction points upward enough to assume sky
        if (dirY > 0.1f) {
            return new MarchResult(true, false, 0, 0, 0, 0);
        }
        
        // Otherwise, consider it occluded (conservative)
        return new MarchResult(false, false, 0, 0, 0, 0);
    }
    
    /**
     * Estimate the direct light hitting a surface.
     * Simple approximation: sky visibility * sky color + block light.
     */
    private float[] estimateSurfaceLight(int x, int y, int z,
                                          WorldAccess world, SkySystem sky, float timeOfDay) {
        // Check sky visibility at this position
        float skyVis = world.getSkyVisibility(x, y, z);
        
        // Get sky color for this time
        float[] zenith = sky.getZenithColor(timeOfDay);
        float[] horizon = sky.getHorizonColor(timeOfDay);
        float skyInt = sky.getSkyIntensity(timeOfDay);
        
        // Blend horizon/zenith (assume mostly horizontal view for surfaces)
        float[] skyColor = new float[] {
            (zenith[0] * 0.3f + horizon[0] * 0.7f) * skyInt,
            (zenith[1] * 0.3f + horizon[1] * 0.7f) * skyInt,
            (zenith[2] * 0.3f + horizon[2] * 0.7f) * skyInt
        };
        
        // Sun contribution (if surface faces up and has sky visibility)
        float[] sunColor = sky.getSunColor(timeOfDay);
        float sunInt = sky.getSunIntensity(timeOfDay);
        
        // Combine: sky ambient + sun direct
        float[] result = new float[3];
        result[0] = skyVis * (skyColor[0] + sunColor[0] * sunInt * 0.5f);
        result[1] = skyVis * (skyColor[1] + sunColor[1] * sunInt * 0.5f);
        result[2] = skyVis * (skyColor[2] + sunColor[2] * sunInt * 0.5f);
        
        // Add block light (warm glow)
        int blockLight = world.getBlockLight(x, y, z);
        if (blockLight > 0) {
            float bl = blockLight / 15.0f;
            result[0] += bl * 1.0f;  // Warm
            result[1] += bl * 0.9f;
            result[2] += bl * 0.7f;
        }
        
        return result;
    }
    
    /**
     * Get approximate albedo (diffuse color) for a block type.
     * Used for color bleed calculation.
     */
    private float[] getBlockAlbedo(int blockId) {
        // Return approximate RGB albedo for common blocks
        if (blockId == Blocks.GRASS.id()) {
            return new float[] {0.3f, 0.5f, 0.2f};  // Green
        }
        if (blockId == Blocks.STONE.id() || blockId == Blocks.COBBLESTONE.id()) {
            return new float[] {0.5f, 0.5f, 0.5f};  // Gray
        }
        if (blockId == Blocks.SAND.id()) {
            return new float[] {0.9f, 0.85f, 0.6f}; // Tan
        }
        if (blockId == Blocks.DIRT.id()) {
            return new float[] {0.5f, 0.35f, 0.2f}; // Brown
        }
        if (blockId == Blocks.LEAVES.id()) {
            return new float[] {0.2f, 0.4f, 0.15f}; // Dark green
        }
        if (blockId == Blocks.LOG.id()) {
            return new float[] {0.55f, 0.4f, 0.25f}; // Brown wood
        }
        if (blockId == Blocks.PLANKS.id()) {
            return new float[] {0.7f, 0.55f, 0.35f}; // Light wood
        }
        if (blockId == Blocks.WATER.id()) {
            return new float[] {0.1f, 0.3f, 0.5f};  // Blue
        }
        if (blockId == Blocks.GRAVEL.id()) {
            return new float[] {0.45f, 0.42f, 0.4f}; // Gray-brown
        }
        
        // Default: neutral gray
        return new float[] {0.5f, 0.5f, 0.5f};
    }
    
    /**
     * Sample irradiance at a position using trilinear interpolation.
     * 
     * @param localX Local X within chunk (0-16)
     * @param localY World Y (0-128)
     * @param localZ Local Z within chunk (0-16)
     * @return RGB irradiance [r, g, b]
     */
    public float[] sampleAt(float localX, float localY, float localZ) {
        // Clamp to valid range
        localX = Math.max(0, Math.min(15.99f, localX));
        localY = Math.max(0, Math.min(127.99f, localY));
        localZ = Math.max(0, Math.min(15.99f, localZ));
        
        // Convert to probe coordinates
        float probeX = localX / PROBE_SPACING - 0.5f;
        float probeY = localY / PROBE_SPACING - 0.5f;
        float probeZ = localZ / PROBE_SPACING - 0.5f;
        
        // Integer and fractional parts
        int px0 = (int) Math.floor(probeX);
        int py0 = (int) Math.floor(probeY);
        int pz0 = (int) Math.floor(probeZ);
        
        float fx = probeX - px0;
        float fy = probeY - py0;
        float fz = probeZ - pz0;
        
        // Clamp to grid bounds
        px0 = Math.max(0, Math.min(PROBE_RES_XZ - 2, px0));
        py0 = Math.max(0, Math.min(PROBE_RES_Y - 2, py0));
        pz0 = Math.max(0, Math.min(PROBE_RES_XZ - 2, pz0));
        
        int px1 = Math.min(px0 + 1, PROBE_RES_XZ - 1);
        int py1 = Math.min(py0 + 1, PROBE_RES_Y - 1);
        int pz1 = Math.min(pz0 + 1, PROBE_RES_XZ - 1);
        
        // Trilinear interpolation
        float invFx = 1 - fx;
        float invFy = 1 - fy;
        float invFz = 1 - fz;
        
        // 8 corner weights
        float w000 = invFx * invFy * invFz;
        float w001 = invFx * invFy * fz;
        float w010 = invFx * fy * invFz;
        float w011 = invFx * fy * fz;
        float w100 = fx * invFy * invFz;
        float w101 = fx * invFy * fz;
        float w110 = fx * fy * invFz;
        float w111 = fx * fy * fz;
        
        // Interpolate R
        float sampledR = 
            r[px0][py0][pz0] * w000 +
            r[px0][py0][pz1] * w001 +
            r[px0][py1][pz0] * w010 +
            r[px0][py1][pz1] * w011 +
            r[px1][py0][pz0] * w100 +
            r[px1][py0][pz1] * w101 +
            r[px1][py1][pz0] * w110 +
            r[px1][py1][pz1] * w111;
        
        // Interpolate G
        float sampledG = 
            g[px0][py0][pz0] * w000 +
            g[px0][py0][pz1] * w001 +
            g[px0][py1][pz0] * w010 +
            g[px0][py1][pz1] * w011 +
            g[px1][py0][pz0] * w100 +
            g[px1][py0][pz1] * w101 +
            g[px1][py1][pz0] * w110 +
            g[px1][py1][pz1] * w111;
        
        // Interpolate B
        float sampledB = 
            b[px0][py0][pz0] * w000 +
            b[px0][py0][pz1] * w001 +
            b[px0][py1][pz0] * w010 +
            b[px0][py1][pz1] * w011 +
            b[px1][py0][pz0] * w100 +
            b[px1][py0][pz1] * w101 +
            b[px1][py1][pz0] * w110 +
            b[px1][py1][pz1] * w111;
        
        return new float[] {sampledR, sampledG, sampledB};
    }
    
    // Getters
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public boolean isDirty() { return dirty; }
    public void markDirty() { this.dirty = true; }
    public int getUpdatePriority() { return updatePriority; }
    public void setUpdatePriority(int priority) { this.updatePriority = priority; }
    
    /**
     * Result of a voxel march operation.
     */
    private static class MarchResult {
        final boolean hitSky;
        final boolean hitBlock;
        final int hitX, hitY, hitZ;
        final int blockId;
        
        MarchResult(boolean hitSky, boolean hitBlock, int hitX, int hitY, int hitZ, int blockId) {
            this.hitSky = hitSky;
            this.hitBlock = hitBlock;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
            this.blockId = blockId;
        }
    }
}
