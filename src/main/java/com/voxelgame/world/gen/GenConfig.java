package com.voxelgame.world.gen;

/**
 * World generation configuration. Holds ALL tuneable parameters for terrain,
 * caves, ores, trees, and special world types.
 *
 * Can be created from a WorldGenPreset or customized via the advanced settings UI.
 * All fields are public for easy slider binding; defaults match Infdev 611 style.
 *
 * === Field groups ===
 * 1. Terrain shape (noise scales, height range, stretch)
 * 2. Surface painting (dirt depth, beach, mountains)
 * 3. Caves (frequency, threshold, depth range)
 * 4. Ores (per-type Y range, vein size, attempts; global abundance multiplier)
 * 5. Trees (patch chance, density multiplier, height range)
 * 6. Special modes (flat world, floating islands)
 */
public class GenConfig {

    // ========================================================
    // Terrain shape (Infdev 611 3D density parameters)
    // ========================================================

    /** Sea level — water fills to this Y. Also affects surface painting. */
    public int seaLevel = 64;

    /** Global terrain height multiplier. 1.0 = normal, 2.0 = doubled. */
    public double terrainHeightScale = 1.0;

    /** Vertical stretch factor for density. Lower = taller features. Default 12.0 */
    public double terrainStretchY = 12.0;

    /** Base size for density offset calculation. Higher = more solid below. */
    public double baseSize = 8.5;

    // InfDev height layers
    public double heightLowScale = 6.0;
    public double heightLowOffset = -4.0;
    public double heightHighScale = 5.0;
    public double heightHighOffset = 6.0;

    // Legacy 2D terrain (kept for GenContext fallback)
    public int baseHeight = 64;
    public double terrainScale = 1.3;
    public int terrainOctaves = 8;
    public int selectorOctaves = 6;

    // ========================================================
    // Surface painting
    // ========================================================
    public int dirtDepth = 4;
    public int mountainThreshold = 100;
    public int beachDepth = 4;
    public double beachNoiseScale = 1.0;
    public int beachNoiseOctaves = 8;

    // ========================================================
    // Caves
    // ========================================================
    public boolean cavesEnabled = true;
    public double caveFreq = 0.045;
    public double caveThreshold = 0.42;
    public int caveMinY = 2;
    public int caveSurfaceMargin = 5;
    public double verticalCaveFreq = 0.06;
    public double verticalCaveThreshold = 0.03;

    // ========================================================
    // Ores
    // ========================================================
    public boolean oresEnabled = true;
    /** Global multiplier for ore attempt counts. 1.0 = normal. */
    public double oreAbundanceMultiplier = 1.0;

    public int coalMinY = 0, coalMaxY = 128, coalVeinSize = 16, coalAttemptsPerChunk = 20;
    public int ironMinY = 0, ironMaxY = 64, ironVeinSize = 8, ironAttemptsPerChunk = 20;
    public int goldMinY = 0, goldMaxY = 32, goldVeinSize = 8, goldAttemptsPerChunk = 2;
    public int diamondMinY = 0, diamondMaxY = 16, diamondVeinSize = 7, diamondAttemptsPerChunk = 1;
    public int redstoneMinY = 0, redstoneMaxY = 16, redstoneVeinSize = 7, redstoneAttemptsPerChunk = 8;

    // ========================================================
    // Trees & vegetation
    // ========================================================
    /** Global tree density multiplier. 1.0 = normal, 2.0 = double trees. */
    public double treeDensityMultiplier = 1.0;

    public double treePatchChance = 0.55;
    public int treePatchAttempts = 12;
    public int treePatchSpread = 5;
    public int treeMinTrunk = 4;
    public int treeMaxTrunk = 7;
    public int treeEdgeMargin = 3;
    public int treeSlopeMax = 3;
    public double forestNoiseThreshold = 0.1;

    // ========================================================
    // Special: Flat world
    // ========================================================
    public boolean flatWorld = false;
    /** Layer block IDs from bottom (y=0) upward. */
    public int[] flatLayers = null;
    /** Base height for flat world (Y of the grass layer). */
    public int flatWorldHeight = 4;

    // ========================================================
    // Special: Floating Islands
    // ========================================================
    public boolean floatingIslands = false;
    public int islandMinY = 40;
    public int islandMaxY = 110;
    public double islandDensityThreshold = 0.15;

    // ========================================================
    // Preset tracking
    // ========================================================
    /** The preset this config was created from (null if custom). */
    public String presetName = "DEFAULT";

    /** Default config suitable for InfDev 611-style terrain. */
    public static GenConfig defaultConfig() {
        return new GenConfig();
    }

    /**
     * Get the effective ore attempts for a given base attempt count,
     * applying the global abundance multiplier.
     */
    public int effectiveOreAttempts(int baseAttempts) {
        return Math.max(0, (int) Math.round(baseAttempts * oreAbundanceMultiplier));
    }

    /**
     * Deep copy this config for safe modification.
     */
    public GenConfig copy() {
        GenConfig c = new GenConfig();
        // Terrain
        c.seaLevel = this.seaLevel;
        c.terrainHeightScale = this.terrainHeightScale;
        c.terrainStretchY = this.terrainStretchY;
        c.baseSize = this.baseSize;
        c.heightLowScale = this.heightLowScale;
        c.heightLowOffset = this.heightLowOffset;
        c.heightHighScale = this.heightHighScale;
        c.heightHighOffset = this.heightHighOffset;
        c.baseHeight = this.baseHeight;
        c.terrainScale = this.terrainScale;
        c.terrainOctaves = this.terrainOctaves;
        c.selectorOctaves = this.selectorOctaves;
        // Surface
        c.dirtDepth = this.dirtDepth;
        c.mountainThreshold = this.mountainThreshold;
        c.beachDepth = this.beachDepth;
        c.beachNoiseScale = this.beachNoiseScale;
        c.beachNoiseOctaves = this.beachNoiseOctaves;
        // Caves
        c.cavesEnabled = this.cavesEnabled;
        c.caveFreq = this.caveFreq;
        c.caveThreshold = this.caveThreshold;
        c.caveMinY = this.caveMinY;
        c.caveSurfaceMargin = this.caveSurfaceMargin;
        c.verticalCaveFreq = this.verticalCaveFreq;
        c.verticalCaveThreshold = this.verticalCaveThreshold;
        // Ores
        c.oresEnabled = this.oresEnabled;
        c.oreAbundanceMultiplier = this.oreAbundanceMultiplier;
        c.coalMinY = this.coalMinY; c.coalMaxY = this.coalMaxY;
        c.coalVeinSize = this.coalVeinSize; c.coalAttemptsPerChunk = this.coalAttemptsPerChunk;
        c.ironMinY = this.ironMinY; c.ironMaxY = this.ironMaxY;
        c.ironVeinSize = this.ironVeinSize; c.ironAttemptsPerChunk = this.ironAttemptsPerChunk;
        c.goldMinY = this.goldMinY; c.goldMaxY = this.goldMaxY;
        c.goldVeinSize = this.goldVeinSize; c.goldAttemptsPerChunk = this.goldAttemptsPerChunk;
        c.diamondMinY = this.diamondMinY; c.diamondMaxY = this.diamondMaxY;
        c.diamondVeinSize = this.diamondVeinSize; c.diamondAttemptsPerChunk = this.diamondAttemptsPerChunk;
        // Trees
        c.treeDensityMultiplier = this.treeDensityMultiplier;
        c.treePatchChance = this.treePatchChance;
        c.treePatchAttempts = this.treePatchAttempts;
        c.treePatchSpread = this.treePatchSpread;
        c.treeMinTrunk = this.treeMinTrunk;
        c.treeMaxTrunk = this.treeMaxTrunk;
        c.treeEdgeMargin = this.treeEdgeMargin;
        c.treeSlopeMax = this.treeSlopeMax;
        c.forestNoiseThreshold = this.forestNoiseThreshold;
        // Flat world
        c.flatWorld = this.flatWorld;
        c.flatLayers = this.flatLayers != null ? this.flatLayers.clone() : null;
        c.flatWorldHeight = this.flatWorldHeight;
        // Floating islands
        c.floatingIslands = this.floatingIslands;
        c.islandMinY = this.islandMinY;
        c.islandMaxY = this.islandMaxY;
        c.islandDensityThreshold = this.islandDensityThreshold;
        // Preset
        c.presetName = this.presetName;
        return c;
    }

    @Override
    public String toString() {
        return "GenConfig{preset=" + presetName +
            ", seaLevel=" + seaLevel +
            ", heightScale=" + terrainHeightScale +
            ", caves=" + cavesEnabled +
            ", ores=" + oresEnabled +
            ", treeDensity=" + treeDensityMultiplier +
            ", flat=" + flatWorld +
            ", floatingIslands=" + floatingIslands + "}";
    }
}
