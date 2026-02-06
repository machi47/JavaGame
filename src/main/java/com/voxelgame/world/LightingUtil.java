package com.voxelgame.world;

/**
 * Unified lighting utilities for the RGB lighting model.
 * 
 * Core principle: Render light is the source of truth. Gameplay light (for mob
 * spawning, etc.) is derived from render light via luminance calculation.
 * 
 * RGB → Luminance → Light Level (0-15)
 */
public final class LightingUtil {

    private LightingUtil() {} // Utility class, no instantiation

    // ---- Luminance coefficients (Rec. 709 / sRGB) ----
    private static final float LUMA_R = 0.2126f;
    private static final float LUMA_G = 0.7152f;
    private static final float LUMA_B = 0.0722f;

    /**
     * Reference luminance for light level 15 (full brightness).
     * This is the luminance of a fully sky-lit surface at midday.
     * Calibrated so that:
     *   - Full daylight sky (1.0 visibility, 0.65 sun brightness) → level ~14-15
     *   - Torch light (14 emission, warm color) → level ~12-14
     *   - Dark cave (0 visibility, 0 block light) → level 0
     */
    public static final float L_REF = 0.65f;

    // ---- Sky colors for luminance calculation ----
    // These match the shader's sky color computation
    private static final float[] SKY_COLOR_DAY = {0.53f, 0.68f, 0.90f};
    private static final float[] BLOCK_LIGHT_COLOR = {1.0f, 0.9f, 0.7f}; // warm torch color

    /**
     * Compute perceptual luminance from RGB values.
     * Uses Rec. 709 coefficients (same as sRGB).
     * 
     * @param r Red component (0-1)
     * @param g Green component (0-1)
     * @param b Blue component (0-1)
     * @return Luminance value (0-1 typically, can exceed 1 for HDR)
     */
    public static float computeLuminance(float r, float g, float b) {
        return LUMA_R * r + LUMA_G * g + LUMA_B * b;
    }

    /**
     * Compute a quantized light level (0-15) from RGB components.
     * 
     * @param r Red component (0-1)
     * @param g Green component (0-1)
     * @param b Blue component (0-1)
     * @return Light level 0-15
     */
    public static int computeLightLevel(float r, float g, float b) {
        float L = computeLuminance(r, g, b);
        return Math.clamp(Math.round(15f * L / L_REF), 0, 15);
    }

    /**
     * Compute the effective light level at a position for gameplay purposes
     * (mob spawning, etc.), given sky visibility and block light level.
     * 
     * This simulates what the shader computes:
     *   skyRGB = skyColor * skyVisibility * skyIntensity
     *   blockRGB = blockLightColor * blockLightLevel/15
     *   totalRGB = skyRGB + blockRGB
     *   luminance = dot(totalRGB, lumaCoeffs)
     *   level = 15 * luminance / L_REF
     * 
     * @param skyVisibility Sky visibility at position (0-1, or 0-15 scaled)
     * @param blockLight Block light level at position (0-15)
     * @param sunBrightness Current sun brightness (0-1, from WorldTime)
     * @return Effective light level 0-15 for gameplay
     */
    public static int computeEffectiveLightLevel(float skyVisibility, int blockLight, float sunBrightness) {
        // Normalize inputs
        float skyVis = skyVisibility;
        if (skyVisibility > 1.0f) {
            // Handle legacy 0-15 scale
            skyVis = skyVisibility / 15.0f;
        }
        float blkL = blockLight / 15.0f;

        // Compute sky contribution
        // Sky color modulated by visibility and sun brightness
        float skyR = SKY_COLOR_DAY[0] * skyVis * sunBrightness;
        float skyG = SKY_COLOR_DAY[1] * skyVis * sunBrightness;
        float skyB = SKY_COLOR_DAY[2] * skyVis * sunBrightness;

        // Compute block light contribution (warm color)
        float blkR = BLOCK_LIGHT_COLOR[0] * blkL;
        float blkG = BLOCK_LIGHT_COLOR[1] * blkL;
        float blkB = BLOCK_LIGHT_COLOR[2] * blkL;

        // Additive combination
        float totalR = skyR + blkR;
        float totalG = skyG + blkG;
        float totalB = skyB + blkB;

        return computeLightLevel(totalR, totalG, totalB);
    }

    /**
     * Simplified light level computation for spawn checks.
     * Uses max of sky-derived and block-derived levels for compatibility
     * with existing spawn logic (which expects "is it bright enough?").
     * 
     * @param skyVisibility Sky visibility (0-1 or 0-15)
     * @param blockLight Block light level (0-15)
     * @param sunBrightness Current sun brightness (0-1)
     * @return Max of sky-based and block-based light level (0-15)
     */
    public static int computeSpawnLightLevel(float skyVisibility, int blockLight, float sunBrightness) {
        // For spawn checks, we want behavior similar to old system:
        // - During day, sky-lit areas are bright (spawns blocked)
        // - During night, sky visibility doesn't help much
        // - Block light always counts regardless of time
        
        float skyVis = skyVisibility > 1.0f ? skyVisibility / 15.0f : skyVisibility;
        
        // Sky-based level: visibility * sun brightness * 15
        // At midday (sunBrightness=0.65), full visibility gives ~10
        // At night (sunBrightness=0.05), full visibility gives ~1
        int skyLevel = Math.round(skyVis * sunBrightness * 15.0f / L_REF);
        
        // Block light is direct (torch at level 14 → gameplay level ~14)
        int blkLevel = blockLight;
        
        return Math.clamp(Math.max(skyLevel, blkLevel), 0, 15);
    }

    /**
     * Check if a position is dark enough for hostile mob spawning.
     * 
     * @param skyVisibility Sky visibility (0-1 or 0-15)
     * @param blockLight Block light level (0-15)
     * @param sunBrightness Current sun brightness (0-1)
     * @param threshold Light level threshold (typically 7)
     * @return true if light level is below threshold (dark enough for hostiles)
     */
    public static boolean isDarkForHostiles(float skyVisibility, int blockLight, 
                                            float sunBrightness, int threshold) {
        return computeSpawnLightLevel(skyVisibility, blockLight, sunBrightness) < threshold;
    }

    /**
     * Check if a position is bright enough for passive mob spawning.
     * 
     * @param skyVisibility Sky visibility (0-1 or 0-15)
     * @param blockLight Block light level (0-15)
     * @param sunBrightness Current sun brightness (0-1)
     * @param threshold Light level threshold (typically 7)
     * @return true if light level is at or above threshold (bright enough for passives)
     */
    public static boolean isBrightForPassives(float skyVisibility, int blockLight,
                                              float sunBrightness, int threshold) {
        return computeSpawnLightLevel(skyVisibility, blockLight, sunBrightness) >= threshold;
    }
}
