package com.voxelgame.world.gen;

import com.voxelgame.math.CombinedNoise;
import com.voxelgame.math.OctaveNoise;
import com.voxelgame.math.Perlin;
import com.voxelgame.math.RNG;
import com.voxelgame.world.WorldConstants;

/**
 * Per-world generation context. Carries the seed, noise instances,
 * config, and scratch buffers shared across generation passes.
 *
 * Terrain generation uses InfDev 611-style combined noise:
 * - Two CombinedNoise fields for dual height layers (low/high)
 * - A selector noise to choose between them
 * - Domain warping creates the characteristic varied terrain
 *
 * Classic Minecraft's octave noise returns [-128, 128] for 8 octaves
 * (unnormalized sum). Our OctaveNoise normalizes to [-1, 1], so we
 * multiply by 128 to match Classic's range.
 *
 * Created once and shared (thread-safe — all noise generators are immutable after init).
 */
public class GenContext {

    private final long seed;
    private final GenConfig config;

    // Classic-range amplitude factor: OctaveNoise normalizes to [-1,1],
    // Classic returns [-128, 128] for 8 octaves.
    // We use a higher value to compensate for CombinedNoise not reaching
    // full range (the warping distributes values away from extremes).
    private static final double CLASSIC_AMPLITUDE = 260.0;

    // InfDev-style combined noise for terrain (two height layers)
    private final CombinedNoise combinedNoise1; // for heightLow
    private final CombinedNoise combinedNoise2; // for heightHigh
    private final OctaveNoise selectorNoise;    // chooses between low/high

    // Cave noise generators
    private final Perlin caveNoise1;
    private final Perlin caveNoise2;
    private final Perlin caveNoise3; // for vertical shafts

    // Tree/forest density noise
    private final Perlin treeDensityNoise;
    private final Perlin forestNoise; // larger-scale forest patches

    // Beach noise (determines sand placement near water)
    private final OctaveNoise beachNoise;

    // Erosion noise — creates varied dirt depth
    private final OctaveNoise erosionNoise;

    // Reference to the Infdev611 terrain pass (for height lookups)
    private Infdev611TerrainPass infdev611Terrain;

    /** Set the Infdev 611 terrain pass (called during pipeline construction). */
    public void setInfdev611Terrain(Infdev611TerrainPass terrain) {
        this.infdev611Terrain = terrain;
    }

    public GenContext(long seed, GenConfig config) {
        this.seed = seed;
        this.config = config;

        // InfDev-style combined noise for height calculation
        // CombinedNoise = octave1(x + octave2(x, z), z) — domain warping
        this.combinedNoise1 = new CombinedNoise(
            new OctaveNoise(seed, config.terrainOctaves, 2.0, 0.5),
            new OctaveNoise(seed + 100L, config.terrainOctaves, 2.0, 0.5)
        );
        this.combinedNoise2 = new CombinedNoise(
            new OctaveNoise(seed + 200L, config.terrainOctaves, 2.0, 0.5),
            new OctaveNoise(seed + 300L, config.terrainOctaves, 2.0, 0.5)
        );
        this.selectorNoise = new OctaveNoise(seed + 400L, config.selectorOctaves, 2.0, 0.5);

        // Initialize cave noise (two Perlin layers for spaghetti caves + one for vertical)
        this.caveNoise1 = new Perlin(seed + 2000L);
        this.caveNoise2 = new Perlin(seed + 3000L);
        this.caveNoise3 = new Perlin(seed + 3500L);

        // Tree/forest density noise
        this.treeDensityNoise = new Perlin(seed + 4000L);
        this.forestNoise = new Perlin(seed + 4500L);

        // Beach noise (octave-8, like Classic)
        this.beachNoise = new OctaveNoise(seed + 5000L, config.beachNoiseOctaves, 2.0, 0.5);

        // Erosion noise for variable dirt depth
        this.erosionNoise = new OctaveNoise(seed + 6000L, 8, 2.0, 0.5);
    }

    public long getSeed() { return seed; }
    public GenConfig getConfig() { return config; }

    public CombinedNoise getCombinedNoise1() { return combinedNoise1; }
    public CombinedNoise getCombinedNoise2() { return combinedNoise2; }
    public OctaveNoise getSelectorNoise() { return selectorNoise; }
    public Perlin getCaveNoise1() { return caveNoise1; }
    public Perlin getCaveNoise2() { return caveNoise2; }
    public Perlin getCaveNoise3() { return caveNoise3; }
    public Perlin getTreeDensityNoise() { return treeDensityNoise; }
    public Perlin getForestNoise() { return forestNoise; }
    public OctaveNoise getBeachNoise() { return beachNoise; }
    public OctaveNoise getErosionNoise() { return erosionNoise; }

    /**
     * Create an RNG seeded for a specific chunk position.
     * Deterministic: same chunk always gets same RNG.
     */
    public RNG chunkRNG(int chunkX, int chunkZ) {
        return new RNG(seed).derive(chunkX, chunkZ);
    }

    /**
     * Compute terrain height at a world (x, z) coordinate.
     *
     * If the Infdev 611 terrain pass is available, delegates to its 3D density-based
     * height calculation. Otherwise falls back to the legacy 2D heightmap.
     */
    public int getTerrainHeight(int worldX, int worldZ) {
        // Use Infdev 611 3D density terrain height if available
        if (infdev611Terrain != null) {
            return infdev611Terrain.getTerrainHeight(worldX, worldZ);
        }

        // Legacy fallback: 2D combined noise heightmap
        double sx = worldX * 0.013;
        double sz = worldZ * 0.013;

        double raw1 = combinedNoise1.eval2D(sx, sz) * CLASSIC_AMPLITUDE;
        double raw2 = combinedNoise2.eval2D(sx, sz) * CLASSIC_AMPLITUDE;

        double heightLow = raw1 / config.heightLowScale + config.heightLowOffset;
        double heightHigh = raw2 / config.heightHighScale + config.heightHighOffset;

        double selector = selectorNoise.eval2D(worldX * 0.005, worldZ * 0.005);

        double heightResult;
        if (selector > 0) {
            heightResult = heightLow;
        } else {
            heightResult = Math.max(heightLow, heightHigh);
        }

        heightResult = heightResult / 2.0;

        if (heightResult < 0) {
            heightResult = heightResult * 0.8;
        }

        int height = config.baseHeight + (int) heightResult;
        return Math.max(1, Math.min(height, WorldConstants.WORLD_HEIGHT - 2));
    }
}
