package com.voxelgame.math;

import java.util.Random;

/**
 * Perlin noise matching Minecraft's original implementation used in Infdev 611 through Beta.
 * This is the core noise primitive. Unlike our standard Perlin which uses modern improved noise,
 * Minecraft's original uses a slightly different gradient function and includes an origin offset.
 *
 * Key differences from standard improved Perlin:
 * - Uses java.util.Random for permutation shuffle (matching Minecraft's seeding)
 * - Includes an origin offset (xo, yo, zo) for each octave instance
 * - The grad function uses Minecraft's original bit-manipulation approach
 */
public class Infdev611Noise {

    private final int[] perm = new int[512];
    public final double xo, yo, zo;

    public Infdev611Noise(Random random) {
        // Generate origin offset
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;

        // Initialize permutation table
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }

        // Shuffle using Java's Random (matches Minecraft's seeding)
        for (int i = 0; i < 256; i++) {
            int j = random.nextInt(256 - i) + i;
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }

        // Double the permutation table
        for (int i = 0; i < 256; i++) {
            perm[i] = p[i];
            perm[i + 256] = p[i];
        }
    }

    /** Fade curve: 6t^5 - 15t^4 + 10t^3 */
    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    /** Linear interpolation */
    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** Minecraft's gradient function */
    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /**
     * Sample 3D Perlin noise at the given position.
     * Applies the origin offset before sampling.
     */
    public double sample(double x, double y, double z) {
        double px = x + xo;
        double py = y + yo;
        double pz = z + zo;

        int xi = (int) Math.floor(px);
        int yi = (int) Math.floor(py);
        int zi = (int) Math.floor(pz);

        double xf = px - xi;
        double yf = py - yi;
        double zf = pz - zi;

        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);

        xi &= 255;
        yi &= 255;
        zi &= 255;

        int a  = perm[xi] + yi;
        int aa = perm[a] + zi;
        int ab = perm[a + 1] + zi;
        int b  = perm[xi + 1] + yi;
        int ba = perm[b] + zi;
        int bb = perm[b + 1] + zi;

        return lerp(w,
            lerp(v,
                lerp(u, grad(perm[aa], xf, yf, zf), grad(perm[ba], xf - 1, yf, zf)),
                lerp(u, grad(perm[ab], xf, yf - 1, zf), grad(perm[bb], xf - 1, yf - 1, zf))
            ),
            lerp(v,
                lerp(u, grad(perm[aa + 1], xf, yf, zf - 1), grad(perm[ba + 1], xf - 1, yf, zf - 1)),
                lerp(u, grad(perm[ab + 1], xf, yf - 1, zf - 1), grad(perm[bb + 1], xf - 1, yf - 1, zf - 1))
            )
        );
    }

    /**
     * Sample 2D noise (y = 0, ignoring yOrigin).
     * Used for beachOctaveNoise and other 2D samplers.
     */
    public double sample2D(double x, double z) {
        double px = x + xo;
        double pz = z + zo;

        int xi = (int) Math.floor(px);
        int zi = (int) Math.floor(pz);

        double xf = px - xi;
        double zf = pz - zi;

        double u = fade(xf);
        double w = fade(zf);

        xi &= 255;
        zi &= 255;

        int a  = perm[xi] + 0;
        int aa = perm[a] + zi;
        int b  = perm[xi + 1] + 0;
        int ba = perm[b] + zi;

        // 2D: y=0, no y interpolation needed
        return lerp(w,
            lerp(u, grad(perm[aa], xf, 0, zf), grad(perm[ba], xf - 1, 0, zf)),
            lerp(u, grad(perm[aa + 1], xf, 0, zf - 1), grad(perm[ba + 1], xf - 1, 0, zf - 1))
        );
    }

    /**
     * Sample with scale parameters (used in Alpha/Beta 3D noise arrays).
     * yScale and yOffset are used for the Y-axis wrapping behavior.
     */
    public double sampleWithScale(double x, double y, double z, double yScale, double yOffset) {
        double px = x + xo;
        double py = y + yo;
        double pz = z + zo;

        int xi = (int) Math.floor(px);
        int yi = (int) Math.floor(py);
        int zi = (int) Math.floor(pz);

        double xf = px - xi;
        double yf = py - yi;
        double zf = pz - zi;

        // Y-axis wrapping (Minecraft's specific behavior)
        if (yScale != 0.0) {
            yf = yf - Math.floor(yf / yScale) * yScale;
        }

        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);

        xi &= 255;
        yi &= 255;
        zi &= 255;

        int a  = perm[xi] + yi;
        int aa = perm[a] + zi;
        int ab = perm[a + 1] + zi;
        int b  = perm[xi + 1] + yi;
        int ba = perm[b] + zi;
        int bb = perm[b + 1] + zi;

        return lerp(w,
            lerp(v,
                lerp(u, grad(perm[aa], xf, yf, zf), grad(perm[ba], xf - 1, yf, zf)),
                lerp(u, grad(perm[ab], xf, yf - 1, zf), grad(perm[bb], xf - 1, yf - 1, zf))
            ),
            lerp(v,
                lerp(u, grad(perm[aa + 1], xf, yf, zf - 1), grad(perm[ba + 1], xf - 1, yf, zf - 1)),
                lerp(u, grad(perm[ab + 1], xf, yf - 1, zf - 1), grad(perm[bb + 1], xf - 1, yf - 1, zf - 1))
            )
        );
    }
}
