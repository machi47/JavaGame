package com.voxelgame.math;

import java.util.Random;

/**
 * Octave noise matching Minecraft's PerlinOctaveNoise used in Infdev 611 through Beta.
 * Each octave doubles in frequency and halves in amplitude (unnormalized sum).
 *
 * IMPORTANT: Unlike our OctaveNoise which normalizes to [-1,1], this returns
 * the raw unnormalized sum, matching Minecraft's original behavior.
 * For 8 octaves, range is approximately [-128, 128].
 * For 16 octaves, range is approximately [-32768, 32768].
 */
public class Infdev611OctaveNoise {

    private final Infdev611Noise[] octaves;
    private final int octaveCount;

    /**
     * Create octave noise with the specified number of octaves.
     *
     * @param random     The shared Random instance (advances state for each octave)
     * @param numOctaves Number of octaves
     */
    public Infdev611OctaveNoise(Random random, int numOctaves) {
        this.octaveCount = numOctaves;
        this.octaves = new Infdev611Noise[numOctaves];

        for (int i = 0; i < numOctaves; i++) {
            this.octaves[i] = new Infdev611Noise(random);
        }
    }

    /**
     * Standard 3D noise sample (used for beach noise, surface noise, scale noise, etc.)
     * Returns unnormalized sum.
     */
    public double sample(double x, double y, double z) {
        double total = 0.0;
        double frequency = 1.0;

        for (int i = 0; i < octaveCount; i++) {
            total += octaves[i].sample(x / frequency, y / frequency, z / frequency) * frequency;
            frequency *= 2.0;
        }

        return total;
    }

    /**
     * 2D noise sample (ignores Y origin offset).
     * Used for beachOctaveNoise, etc.
     */
    public double sample2D(double x, double z) {
        double total = 0.0;
        double frequency = 1.0;

        for (int i = 0; i < octaveCount; i++) {
            total += octaves[i].sample2D(x / frequency, z / frequency) * frequency;
            frequency *= 2.0;
        }

        return total;
    }

    /**
     * Alpha/Beta-style 3D noise with scale parameters.
     * Used for the main density noise (minLimit, maxLimit, main).
     *
     * @param x, y, z     Position (pre-scaled by caller)
     * @param scaleX      X coordinate scale
     * @param scaleY      Y coordinate scale
     * @param scaleZ      Z coordinate scale
     */
    public double sampleScaled(double x, double y, double z,
                                double scaleX, double scaleY, double scaleZ) {
        double total = 0.0;
        double frequency = 1.0;

        for (int i = 0; i < octaveCount; i++) {
            double sx = x * scaleX * frequency;
            double sy = y * scaleY * frequency;
            double sz = z * scaleZ * frequency;

            total += octaves[i].sampleWithScale(sx, sy, sz,
                scaleY * frequency, y * scaleY * frequency) / frequency;

            frequency /= 2.0;
        }

        return total;
    }

    /**
     * Scale-aware 2D sample (used for depth/scale noise).
     * Samples with scaleX and scaleZ but Y=0.
     */
    public double sampleScaled2D(double x, double z, double scaleX, double yDummy, double scaleZ) {
        double total = 0.0;
        double frequency = 1.0;

        for (int i = 0; i < octaveCount; i++) {
            double sx = x * scaleX * frequency;
            double sy = 0.0;
            double sz = z * scaleZ * frequency;

            // Use the 3D sampler with y=0 but let it use the origin offset
            total += octaves[i].sampleWithScale(sx, sy, sz,
                0.0, 0.0) / frequency;

            frequency /= 2.0;
        }

        return total;
    }
}
