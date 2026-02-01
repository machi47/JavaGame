package com.voxelgame.world.gen;

import com.voxelgame.world.Chunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the generation pass pipeline. Runs passes in order:
 * base terrain → surface paint → caves → fluids → ores → trees.
 * Thread-safe: the pipeline and context are immutable after construction.
 * Each pass receives its own chunk reference to modify in place.
 */
public class GenPipeline {

    @FunctionalInterface
    public interface GenerationPass {
        void apply(Chunk chunk, GenContext context);
    }

    private final GenContext context;
    private final List<GenerationPass> passes;

    public GenPipeline(GenContext context) {
        this.context = context;
        this.passes = new ArrayList<>();
    }

    /** Add a pass to the end of the pipeline. */
    public GenPipeline addPass(GenerationPass pass) {
        passes.add(pass);
        return this;
    }

    /** Run all passes on the given chunk, in order. */
    public void generate(Chunk chunk) {
        for (GenerationPass pass : passes) {
            pass.apply(chunk, context);
        }
    }

    /** Get the context (for spawn point finding, etc.) */
    public GenContext getContext() {
        return context;
    }

    /**
     * Build the default pipeline with all passes in correct order.
     * Uses authentic Infdev 611 3D density-based terrain generation.
     */
    public static GenPipeline createDefault(long seed) {
        GenConfig config = GenConfig.defaultConfig();
        GenContext context = new GenContext(seed, config);
        GenPipeline pipeline = new GenPipeline(context);

        // Create the Infdev 611 terrain pass (3D density-based)
        Infdev611TerrainPass terrainPass = new Infdev611TerrainPass(seed);

        // Store terrain pass reference in context for height lookups
        context.setInfdev611Terrain(terrainPass);

        // Pipeline order:
        // 1. 3D density terrain (stone/air)
        // 2. Surface painting (grass/dirt/sand/beaches/bedrock/water)
        // 3. Cave carving
        // 4. Ore veins
        // 5. Trees
        // 6. Flowers
        pipeline.addPass(terrainPass);
        pipeline.addPass(new Infdev611SurfacePass(
            terrainPass.getBeachNoise(),
            terrainPass.getSurfaceNoise(),
            seed));
        pipeline.addPass(new CarveCavesPass());
        pipeline.addPass(new OreVeinsPass());
        pipeline.addPass(new TreesPass());
        pipeline.addPass(new FlowersPass());

        return pipeline;
    }
}
