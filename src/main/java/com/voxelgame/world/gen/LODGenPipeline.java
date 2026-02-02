package com.voxelgame.world.gen;

import com.voxelgame.world.Chunk;

/**
 * Simplified generation pipeline for LOD 2+ chunks.
 * Only generates base terrain + surface paint (no caves, ores, trees, etc.).
 * Much faster than the full pipeline since distant chunks only show the surface.
 */
public class LODGenPipeline {

    private final GenContext context;
    private final GenPipeline.GenerationPass terrainPass;
    private final GenPipeline.GenerationPass surfacePass;

    /**
     * Create a simplified LOD generation pipeline.
     */
    public LODGenPipeline(long seed) {
        GenConfig config = GenConfig.defaultConfig();
        this.context = new GenContext(seed, config);

        // Only need terrain + surface (skip caves, ores, trees, flowers, fluids)
        Infdev611TerrainPass terrain = new Infdev611TerrainPass(seed);
        context.setInfdev611Terrain(terrain);

        this.terrainPass = terrain;
        this.surfacePass = new Infdev611SurfacePass(
            terrain.getBeachNoise(),
            terrain.getSurfaceNoise(),
            seed);
    }

    /**
     * Generate a simplified chunk (terrain + surface only).
     * ~3-5x faster than full pipeline.
     */
    public void generateSimplified(Chunk chunk) {
        terrainPass.apply(chunk, context);
        surfacePass.apply(chunk, context);
    }

    public GenContext getContext() {
        return context;
    }
}
