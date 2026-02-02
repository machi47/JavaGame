package com.voxelgame.world.gen;

import com.voxelgame.world.Chunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the generation pass pipeline. Runs passes in order:
 * base terrain → surface paint → caves → fluids → ores → trees → flowers.
 *
 * Supports multiple world generation modes via GenConfig:
 * - Standard 3D density terrain (Infdev 611)
 * - Flat world (Superflat preset)
 * - Floating islands
 * - Amplified terrain
 * - Ocean-heavy worlds
 *
 * Thread-safe: the pipeline and context are immutable after construction.
 */
public class GenPipeline {

    @FunctionalInterface
    public interface GenerationPass {
        void apply(Chunk chunk, GenContext context);
    }

    private final GenContext context;
    private final List<GenerationPass> passes;
    private final GenConfig config;

    public GenPipeline(GenContext context) {
        this.context = context;
        this.config = context.getConfig();
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

    /** Get the config this pipeline was built with. */
    public GenConfig getConfig() {
        return config;
    }

    /**
     * Build the default pipeline using the default preset.
     */
    public static GenPipeline createDefault(long seed) {
        return createWithConfig(seed, GenConfig.defaultConfig());
    }

    /**
     * Build a pipeline from a WorldGenPreset.
     */
    public static GenPipeline createFromPreset(long seed, WorldGenPreset preset) {
        GenConfig config = preset.createConfig();
        config.presetName = preset.name();
        return createWithConfig(seed, config);
    }

    /**
     * Build a pipeline from a custom GenConfig.
     * Selects the appropriate passes based on config flags:
     * - flatWorld → FlatWorldPass instead of terrain
     * - floatingIslands → FloatingIslandsPass instead of terrain
     * - cavesEnabled → CarveCavesPass
     * - oresEnabled → OreVeinsPass
     */
    public static GenPipeline createWithConfig(long seed, GenConfig config) {
        GenContext context = new GenContext(seed, config);
        GenPipeline pipeline = new GenPipeline(context);

        if (config.flatWorld) {
            // ---- Flat world pipeline ----
            pipeline.addPass(new FlatWorldPass());
            // Light trees on flat worlds if density > 0
            if (config.treeDensityMultiplier > 0) {
                pipeline.addPass(new TreesPass());
            }
            pipeline.addPass(new FlowersPass());

        } else if (config.floatingIslands) {
            // ---- Floating islands pipeline ----
            FloatingIslandsPass islandPass = new FloatingIslandsPass(seed, config);
            context.setInfdev611Terrain(null); // No standard terrain pass
            pipeline.addPass(islandPass);
            pipeline.addPass(new IslandSurfacePass(seed));
            if (config.cavesEnabled) {
                pipeline.addPass(new CarveCavesPass());
            }
            if (config.oresEnabled) {
                pipeline.addPass(new OreVeinsPass());
            }
            pipeline.addPass(new TreesPass());
            pipeline.addPass(new FlowersPass());

        } else {
            // ---- Standard terrain pipeline (Default / Amplified / More Oceans) ----
            Infdev611TerrainPass terrainPass = new Infdev611TerrainPass(seed, config);
            context.setInfdev611Terrain(terrainPass);

            pipeline.addPass(terrainPass);
            pipeline.addPass(new Infdev611SurfacePass(
                terrainPass.getBeachNoise(),
                terrainPass.getSurfaceNoise(),
                seed));

            if (config.cavesEnabled) {
                pipeline.addPass(new CarveCavesPass());
            }
            if (config.oresEnabled) {
                pipeline.addPass(new OreVeinsPass());
            }
            pipeline.addPass(new TreesPass());
            pipeline.addPass(new FlowersPass());
        }

        return pipeline;
    }
}
