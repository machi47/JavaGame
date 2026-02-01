package com.voxelgame.world.gen;

import com.voxelgame.math.Infdev611OctaveNoise;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

import java.util.Random;

/**
 * Authentic Infdev 20100611 terrain generation using 3D density sampling.
 *
 * Unlike the old BaseTerrainPass (2D heightmap), this samples noise at every (x,y,z)
 * to determine whether a block is solid or air. This naturally creates overhangs,
 * cliffs, and the famous monoliths.
 *
 * The algorithm:
 * 1. For each 4x8x4 noise cell (interpolated to block resolution):
 *    a. Sample 2D scale noise (10 octaves) → hillFactor
 *    b. Sample 2D depth noise (16 octaves) → base terrain height
 *    c. Process depth with the Infdev 611 formula (NO depth/=2 dampening)
 *    d. For each Y level, compute density from 3D noise (min/max/main interpolation)
 *    e. density > 0 → stone, else → air
 *
 * The key Infdev 611 quirk: omitting `depth /= 2` creates extreme terrain
 * that can reach the height limit, producing monoliths.
 */
public class Infdev611TerrainPass implements GenPipeline.GenerationPass {

    // Noise resolution: sample every N blocks, then interpolate
    private static final int NOISE_HORIZONTAL_RESOLUTION = 4;  // sample every 4 blocks in X/Z
    private static final int NOISE_VERTICAL_RESOLUTION = 8;    // sample every 8 blocks in Y

    // Number of noise samples per chunk
    private static final int NOISE_SIZE_X = WorldConstants.CHUNK_SIZE / NOISE_HORIZONTAL_RESOLUTION + 1; // 5
    private static final int NOISE_SIZE_Z = WorldConstants.CHUNK_SIZE / NOISE_HORIZONTAL_RESOLUTION + 1; // 5
    private static final int NOISE_SIZE_Y = WorldConstants.WORLD_HEIGHT / NOISE_VERTICAL_RESOLUTION + 1; // 17

    // Terrain parameters (matching Minecraft Infdev 611 / Alpha defaults)
    private static final double COORDINATE_SCALE = 684.412;
    private static final double HEIGHT_SCALE = 684.412;
    private static final double UPPER_LIMIT_SCALE = 512.0;
    private static final double LOWER_LIMIT_SCALE = 512.0;
    private static final double DEPTH_NOISE_SCALE_X = 200.0;
    private static final double DEPTH_NOISE_SCALE_Z = 200.0;
    private static final double MAIN_NOISE_SCALE_X = 80.0;
    private static final double MAIN_NOISE_SCALE_Y = 160.0;
    private static final double MAIN_NOISE_SCALE_Z = 80.0;
    private static final double BASE_SIZE = 8.5;
    private static final double STRETCH_Y = 12.0;

    // Noise generators
    private final Infdev611OctaveNoise minLimitNoise;
    private final Infdev611OctaveNoise maxLimitNoise;
    private final Infdev611OctaveNoise mainNoise;
    private final Infdev611OctaveNoise scaleNoise;  // 10 octaves, 2D "hill noise"
    private final Infdev611OctaveNoise depthNoise;  // 16 octaves, 2D base height

    // Additional noise for surface (exposed for SurfacePaintPass)
    final Infdev611OctaveNoise beachNoise;     // 4 octaves
    final Infdev611OctaveNoise surfaceNoise;   // 4 octaves
    final Infdev611OctaveNoise forestNoise;    // 8 octaves

    public Infdev611TerrainPass(long seed) {
        // Initialize all noise generators from a single Random, in order
        // This matches Minecraft's initialization sequence exactly
        Random random = new Random(seed);

        this.minLimitNoise = new Infdev611OctaveNoise(random, 16);
        this.maxLimitNoise = new Infdev611OctaveNoise(random, 16);
        this.mainNoise = new Infdev611OctaveNoise(random, 8);
        this.beachNoise = new Infdev611OctaveNoise(random, 4);
        this.surfaceNoise = new Infdev611OctaveNoise(random, 4);
        this.scaleNoise = new Infdev611OctaveNoise(random, 10);
        this.depthNoise = new Infdev611OctaveNoise(random, 16);
        this.forestNoise = new Infdev611OctaveNoise(random, 8);
    }

    @Override
    public void apply(Chunk chunk, GenContext context) {
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();

        // Compute noise column origins in noise-space
        int startNoiseX = chunkX * (WorldConstants.CHUNK_SIZE / NOISE_HORIZONTAL_RESOLUTION);
        int startNoiseZ = chunkZ * (WorldConstants.CHUNK_SIZE / NOISE_HORIZONTAL_RESOLUTION);

        // Sample density at all noise grid points for this chunk
        double[][][] densityGrid = new double[NOISE_SIZE_X][NOISE_SIZE_Y][NOISE_SIZE_Z];

        for (int nx = 0; nx < NOISE_SIZE_X; nx++) {
            for (int nz = 0; nz < NOISE_SIZE_Z; nz++) {
                double[] column = new double[NOISE_SIZE_Y];
                sampleNoiseColumn(column, startNoiseX + nx, startNoiseZ + nz);
                for (int ny = 0; ny < NOISE_SIZE_Y; ny++) {
                    densityGrid[nx][ny][nz] = column[ny];
                }
            }
        }

        // Interpolate density to block resolution and fill chunk
        for (int nx = 0; nx < NOISE_SIZE_X - 1; nx++) {
            for (int nz = 0; nz < NOISE_SIZE_Z - 1; nz++) {
                for (int ny = 0; ny < NOISE_SIZE_Y - 1; ny++) {
                    // 8 corner densities of this noise cell
                    double d000 = densityGrid[nx][ny][nz];
                    double d100 = densityGrid[nx + 1][ny][nz];
                    double d010 = densityGrid[nx][ny + 1][nz];
                    double d110 = densityGrid[nx + 1][ny + 1][nz];
                    double d001 = densityGrid[nx][ny][nz + 1];
                    double d101 = densityGrid[nx + 1][ny][nz + 1];
                    double d011 = densityGrid[nx][ny + 1][nz + 1];
                    double d111 = densityGrid[nx + 1][ny + 1][nz + 1];

                    // Step sizes for trilinear interpolation
                    double stepY00 = (d010 - d000) / NOISE_VERTICAL_RESOLUTION;
                    double stepY10 = (d110 - d100) / NOISE_VERTICAL_RESOLUTION;
                    double stepY01 = (d011 - d001) / NOISE_VERTICAL_RESOLUTION;
                    double stepY11 = (d111 - d101) / NOISE_VERTICAL_RESOLUTION;

                    double yBase00 = d000;
                    double yBase10 = d100;
                    double yBase01 = d001;
                    double yBase11 = d101;

                    for (int dy = 0; dy < NOISE_VERTICAL_RESOLUTION; dy++) {
                        int blockY = ny * NOISE_VERTICAL_RESOLUTION + dy;
                        if (blockY >= WorldConstants.WORLD_HEIGHT) break;

                        // Interpolate along X at z=0 and z=1
                        double stepX0 = (yBase10 - yBase00) / NOISE_HORIZONTAL_RESOLUTION;
                        double stepX1 = (yBase11 - yBase01) / NOISE_HORIZONTAL_RESOLUTION;

                        double xBase0 = yBase00;
                        double xBase1 = yBase01;

                        for (int dx = 0; dx < NOISE_HORIZONTAL_RESOLUTION; dx++) {
                            int localX = nx * NOISE_HORIZONTAL_RESOLUTION + dx;
                            if (localX >= WorldConstants.CHUNK_SIZE) break;

                            // Interpolate along Z
                            double stepZ = (xBase1 - xBase0) / NOISE_HORIZONTAL_RESOLUTION;
                            double density = xBase0;

                            for (int dz = 0; dz < NOISE_HORIZONTAL_RESOLUTION; dz++) {
                                int localZ = nz * NOISE_HORIZONTAL_RESOLUTION + dz;
                                if (localZ >= WorldConstants.CHUNK_SIZE) break;

                                // Density > 0 = solid (stone), <= 0 = air
                                if (density > 0) {
                                    chunk.setBlock(localX, blockY, localZ, Blocks.STONE.id());
                                } else {
                                    chunk.setBlock(localX, blockY, localZ, Blocks.AIR.id());
                                }

                                density += stepZ;
                            }

                            xBase0 += stepX0;
                            xBase1 += stepX1;
                        }

                        yBase00 += stepY00;
                        yBase10 += stepY10;
                        yBase01 += stepY01;
                        yBase11 += stepY11;
                    }
                }
            }
        }
    }

    /**
     * Sample noise density at all Y levels for a given noise column.
     * This is the heart of the Infdev 611 terrain generator.
     */
    private void sampleNoiseColumn(double[] column, int noiseX, int noiseZ) {
        // 2D noise: scale (hill factor) and depth (base height)
        double scaleVal = scaleNoise.sampleScaled2D(noiseX, noiseZ, 1.0, 0.0, 1.0);
        double depthVal = depthNoise.sampleScaled2D(noiseX, noiseZ, DEPTH_NOISE_SCALE_X, 0.0, DEPTH_NOISE_SCALE_Z);

        // Process scale → hill factor
        double scale = (scaleVal + 256.0) / 512.0;
        if (scale > 1.0) scale = 1.0;

        // Process depth → base terrain height
        double depth = depthVal / 8000.0;

        if (depth < 0.0) {
            depth = -depth;
        }

        depth = depth * 3.0 - 3.0;

        if (depth < 0.0) {
            depth /= 2.0;
            if (depth < -1.0) {
                depth = -1.0;
            }
            depth /= 1.4;
            // === INFDEV 611 CRITICAL DIFFERENCE ===
            // Alpha adds: depth /= 2.0;
            // Infdev 611 OMITS this line, creating extreme terrain and monoliths
            scale = 0.0;
        } else {
            if (depth > 1.0) {
                depth = 1.0;
            }
            depth /= 6.0;
        }

        scale += 0.5;
        depth = depth * BASE_SIZE / 8.0;
        depth = BASE_SIZE + depth * 4.0;

        // Sample density at each Y level
        for (int noiseY = 0; noiseY < NOISE_SIZE_Y; noiseY++) {
            // Density offset: makes lower Y more likely solid, higher Y more likely air
            double densityOffset = ((double) noiseY - depth) * STRETCH_Y / scale;
            if (densityOffset < 0.0) {
                densityOffset *= 4.0;
            }

            // 3D noise sampling for density
            double mainSample = (mainNoise.sampleScaled(
                noiseX, noiseY, noiseZ,
                COORDINATE_SCALE / MAIN_NOISE_SCALE_X,
                HEIGHT_SCALE / MAIN_NOISE_SCALE_Y,
                COORDINATE_SCALE / MAIN_NOISE_SCALE_Z
            ) / 10.0 + 1.0) / 2.0;

            double density;

            if (mainSample < 0.0) {
                density = minLimitNoise.sampleScaled(
                    noiseX, noiseY, noiseZ,
                    COORDINATE_SCALE, HEIGHT_SCALE, COORDINATE_SCALE
                ) / LOWER_LIMIT_SCALE;
            } else if (mainSample > 1.0) {
                density = maxLimitNoise.sampleScaled(
                    noiseX, noiseY, noiseZ,
                    COORDINATE_SCALE, HEIGHT_SCALE, COORDINATE_SCALE
                ) / UPPER_LIMIT_SCALE;
            } else {
                double minLimit = minLimitNoise.sampleScaled(
                    noiseX, noiseY, noiseZ,
                    COORDINATE_SCALE, HEIGHT_SCALE, COORDINATE_SCALE
                ) / LOWER_LIMIT_SCALE;

                double maxLimit = maxLimitNoise.sampleScaled(
                    noiseX, noiseY, noiseZ,
                    COORDINATE_SCALE, HEIGHT_SCALE, COORDINATE_SCALE
                ) / UPPER_LIMIT_SCALE;

                density = minLimit + (maxLimit - minLimit) * mainSample;
            }

            density -= densityOffset;

            // Top slide: fade to air at world top
            if (noiseY > NOISE_SIZE_Y - 4) {
                double slideProgress = (double)(noiseY - (NOISE_SIZE_Y - 4)) / 3.0;
                density = density * (1.0 - slideProgress) + (-10.0) * slideProgress;
            }

            column[noiseY] = density;
        }
    }

    /**
     * Get the approximate terrain height at a world coordinate.
     * Used by other passes (surface painting, trees, etc.) that need the surface Y.
     * Samples the density column and finds the highest solid block.
     */
    public int getTerrainHeight(int worldX, int worldZ) {
        int noiseX = Math.floorDiv(worldX, NOISE_HORIZONTAL_RESOLUTION);
        int noiseZ = Math.floorDiv(worldZ, NOISE_HORIZONTAL_RESOLUTION);

        // Sample a noise column at this position
        double[] column = new double[NOISE_SIZE_Y];
        sampleNoiseColumn(column, noiseX, noiseZ);

        // Find the highest Y where density > 0 (top of terrain)
        for (int ny = NOISE_SIZE_Y - 2; ny >= 0; ny--) {
            int blockY = ny * NOISE_VERTICAL_RESOLUTION + NOISE_VERTICAL_RESOLUTION / 2;
            if (column[ny] > 0 && column[ny + 1] <= 0) {
                // Interpolate to find exact transition
                double ratio = column[ny] / (column[ny] - column[ny + 1]);
                return Math.min(blockY + (int)(ratio * NOISE_VERTICAL_RESOLUTION),
                    WorldConstants.WORLD_HEIGHT - 1);
            }
        }

        // If entirely solid or entirely air, return approximate
        if (column[0] > 0) return WorldConstants.SEA_LEVEL;
        return 1;
    }

    /** Get the beach noise generator (for surface painting). */
    public Infdev611OctaveNoise getBeachNoise() { return beachNoise; }

    /** Get the surface noise generator (for surface painting). */
    public Infdev611OctaveNoise getSurfaceNoise() { return surfaceNoise; }

    /** Get the forest noise generator (for tree placement). */
    public Infdev611OctaveNoise getForestNoise() { return forestNoise; }
}
