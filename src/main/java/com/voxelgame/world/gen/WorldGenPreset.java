package com.voxelgame.world.gen;

/**
 * World generation presets. Each preset creates a distinct GenConfig
 * that tunes terrain, caves, ores, and vegetation for a specific experience.
 *
 * Presets:
 * - DEFAULT:          Standard Infdev 611 terrain (the classic)
 * - AMPLIFIED:        Extreme terrain — taller mountains, deeper valleys
 * - SUPERFLAT:        Flat world — customizable layers, no caves/ores
 * - MORE_OCEANS:      Higher sea level, less land, more water
 * - FLOATING_ISLANDS: Sky islands floating above void, no ground-level terrain
 */
public enum WorldGenPreset {

    DEFAULT("Default", "Classic Infdev 611 terrain with rolling hills and deep caves."),
    AMPLIFIED("Amplified", "Extreme terrain with towering mountains and deep valleys."),
    SUPERFLAT("Superflat", "Flat world with customizable layers. No caves or ores."),
    MORE_OCEANS("More Oceans", "Higher sea level with vast oceans and small islands."),
    FLOATING_ISLANDS("Floating Islands", "Sky islands floating above the void.");

    private final String displayName;
    private final String description;

    WorldGenPreset(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    /**
     * Create a GenConfig tuned for this preset.
     * Each preset modifies the defaults to create a distinct world feel.
     */
    public GenConfig createConfig() {
        return switch (this) {
            case DEFAULT -> GenConfig.defaultConfig();
            case AMPLIFIED -> createAmplifiedConfig();
            case SUPERFLAT -> createSuperflatConfig();
            case MORE_OCEANS -> createMoreOceansConfig();
            case FLOATING_ISLANDS -> createFloatingIslandsConfig();
        };
    }

    // ---- Amplified: extreme terrain ----
    private static GenConfig createAmplifiedConfig() {
        GenConfig c = new GenConfig();
        // Taller, more extreme terrain — bigger height range
        c.terrainHeightScale = 2.2;       // dramatically taller mountains
        c.terrainStretchY = 8.0;          // less vertical compression → taller peaks
        c.baseSize = 6.5;                 // lower base → deeper valleys
        c.heightHighScale = 3.0;          // taller high terrain
        c.heightHighOffset = 12.0;        // push high terrain much higher
        c.heightLowScale = 4.0;           // deeper low terrain
        c.heightLowOffset = -8.0;         // lower valleys
        // More caves to explore in the bigger terrain
        c.caveFreq = 0.04;
        c.caveThreshold = 0.48;
        // More ore to compensate for larger terrain volume
        c.oreAbundanceMultiplier = 1.5;
        // More trees (bigger terrain = more surface)
        c.treeDensityMultiplier = 1.3;
        return c;
    }

    // ---- Superflat: flat world ----
    private static GenConfig createSuperflatConfig() {
        GenConfig c = new GenConfig();
        c.flatWorld = true;
        c.flatLayers = new int[]{
            // bottom to top: 1 bedrock, 3 stone, 8 dirt, 1 grass
            14, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3, 4
        };
        c.flatWorldHeight = 4;  // just above bedrock
        // No terrain features
        c.cavesEnabled = false;
        c.oresEnabled = false;
        c.treeDensityMultiplier = 0.3;  // sparse trees
        return c;
    }

    // ---- More Oceans: higher sea level, less land ----
    private static GenConfig createMoreOceansConfig() {
        GenConfig c = new GenConfig();
        c.seaLevel = 80;                  // raise sea level significantly
        c.baseSize = 10.5;                // push terrain base down
        c.terrainStretchY = 14.0;         // compress terrain vertically
        c.heightHighScale = 6.0;          // less extreme highs
        c.heightHighOffset = 3.0;         // lower peaks
        c.heightLowScale = 7.0;           // shallower lows → more ocean floor
        c.heightLowOffset = -2.0;
        // Fewer caves (less land to put them in)
        c.caveFreq = 0.04;
        c.caveThreshold = 0.38;
        // Less ore (less terrain)
        c.oreAbundanceMultiplier = 0.6;
        // Sparse trees (little land)
        c.treeDensityMultiplier = 0.5;
        return c;
    }

    // ---- Floating Islands: sky islands above void ----
    private static GenConfig createFloatingIslandsConfig() {
        GenConfig c = new GenConfig();
        c.floatingIslands = true;
        c.seaLevel = 0;                   // no sea — void below
        c.baseSize = 8.5;
        c.terrainStretchY = 10.0;
        c.islandMinY = 40;                // lowest island level
        c.islandMaxY = 110;               // highest island level
        c.islandDensityThreshold = 0.15;  // how "thick" islands are
        // Normal caves within islands
        c.caveFreq = 0.05;
        c.caveThreshold = 0.40;
        c.cavesEnabled = true;
        // Less ore
        c.oreAbundanceMultiplier = 0.8;
        // More trees (islands should feel lush)
        c.treeDensityMultiplier = 1.5;
        return c;
    }

    /** Look up preset by name (case-insensitive). Returns DEFAULT if not found. */
    public static WorldGenPreset fromString(String name) {
        if (name == null) return DEFAULT;
        for (WorldGenPreset p : values()) {
            if (p.name().equalsIgnoreCase(name) || p.displayName.equalsIgnoreCase(name)) {
                return p;
            }
        }
        return DEFAULT;
    }
}
