package com.voxelgame.world.gen;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Simplified generation pipeline for LOD 2+ chunks.
 * Supports all world generation presets (flat, floating islands, standard).
 * Only generates the minimum needed for distant chunks (no caves/ores/trees).
 */
public class LODGenPipeline {

    private final GenContext context;
    private final GenConfig config;
    private GenPipeline.GenerationPass terrainPass;
    private GenPipeline.GenerationPass surfacePass;

    /** Create with default config (backward compatible). */
    public LODGenPipeline(long seed) {
        this(seed, GenConfig.defaultConfig());
    }

    /** Create with custom config for preset support. */
    public LODGenPipeline(long seed, GenConfig config) {
        this.config = config;
        this.context = new GenContext(seed, config);

        if (config.flatWorld) {
            // Flat world: just the flat pass
            this.terrainPass = new FlatWorldPass();
            this.surfacePass = null;
        } else if (config.floatingIslands) {
            // Floating islands: island pass + simple surface
            this.terrainPass = new FloatingIslandsPass(seed, config);
            this.surfacePass = new IslandSurfacePass(seed);
        } else {
            // Standard terrain: Infdev611 + surface paint
            Infdev611TerrainPass terrain = new Infdev611TerrainPass(seed, config);
            context.setInfdev611Terrain(terrain);
            this.terrainPass = terrain;
            this.surfacePass = new Infdev611SurfacePass(
                terrain.getBeachNoise(),
                terrain.getSurfaceNoise(),
                seed);
        }
    }

    /**
     * Generate a simplified chunk (terrain + surface only).
     */
    public void generateSimplified(Chunk chunk) {
        terrainPass.apply(chunk, context);
        if (surfacePass != null) {
            surfacePass.apply(chunk, context);
        }
    }

    public GenContext getContext() {
        return context;
    }
}
