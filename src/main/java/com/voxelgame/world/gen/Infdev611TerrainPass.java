package com.voxelgame.world.gen;

import com.voxelgame.math.Infdev611OctaveNoise;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

import java.util.Random;

/**
 * Authentic Infdev 20100611 terrain generation using 3D density sampling.
 * Now configurable via GenConfig for supporting Amplified, More Oceans, etc.
 *
 * The core algorithm is unchanged from Infdev 611:
 * 1. For each 4x8x4 noise cell (interpolated to block resolution):
 *    a. Sample 2D scale noise (10 octaves) → hillFactor
 *    b. Sample 2D depth noise (16 octaves) → base terrain height
 *    c. Process depth with the Infdev 611 formula (NO depth/=2 dampening)
 *    d. For each Y level, compute density from 3D noise (min/max/main interpolation)
 *    e. density > 0 → stone, else → air
 *
 * GenConfig parameters that affect terrain:
 * - terrainHeightScale: multiplies the final density, creating taller/flatter terrain
 * - terrainStretchY: vertical stretch factor (lower = taller features)
 * - baseSize: base density offset (higher = more solid below)
 * - heightLowScale/Offset, heightHighScale/Offset: tune the dual-layer height system
 */
public class Infdev611TerrainPass implements GenPipeline.GenerationPass {

    // Noise resolution
    private static final int NOISE_HORIZONTAL_RESOLUTION = 4;
    private static final int NOISE_VERTICAL_RESOLUTION = 8;
    private static final int NOISE_SIZE_X = WorldConstants.CHUNK_SIZE / NOISE_HORIZONTAL_RESOLUTION + 1;
    private static final int NOISE_SIZE_Z = WorldConstants.CHUNK_SIZE / NOISE_HORIZONTAL_RESOLUTION + 1;
    private static final int NOISE_SIZE_Y = WorldConstants.WORLD_HEIGHT / NOISE_VERTICAL_RESOLUTION + 1;

    // Base terrain parameters (constants — presets modify via GenConfig multipliers)
    private static final double COORDINATE_SCALE = 684.412;
    private static final double HEIGHT_SCALE = 684.412;
    private static final double UPPER_LIMIT_SCALE = 512.0;
    private static final double LOWER_LIMIT_SCALE = 512.0;
    private static final double DEPTH_NOISE_SCALE_X = 200.0;
    private static final double DEPTH_NOISE_SCALE_Z = 200.0;
    private static final double MAIN_NOISE_SCALE_X = 80.0;
    private static final double MAIN_NOISE_SCALE_Y = 160.0;
    private static final double MAIN_NOISE_SCALE_Z = 80.0;

    // Configurable terrain parameters from GenConfig
    private final double baseSize;
    private final double stretchY;
    private final double heightScale;

    // Noise generators
    private final Infdev611OctaveNoise minLimitNoise;
    private final Infdev611OctaveNoise maxLimitNoise;
    private final Infdev611OctaveNoise mainNoise;
    private final Infdev611OctaveNoise scaleNoise;
    private final Infdev611OctaveNoise depthNoise;

    // Additional noise for surface (exposed for SurfacePaintPass)
    final Infdev611OctaveNoise beachNoise;
    final Infdev611OctaveNoise surfaceNoise;
    final Infdev611OctaveNoise forestNoise;

    /** Construct with default config (backward compatible). */
    public Infdev611TerrainPass(long seed) {
        this(seed, GenConfig.defaultConfig());
    }

    /** Construct with custom config for preset support. */
    public Infdev611TerrainPass(long seed, GenConfig config) {
        this.baseSize = config.baseSize;
        this.stretchY = config.terrainStretchY;
        this.heightScale = config.terrainHeightScale;

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

        int startNoiseX = chunkX * (WorldConstants.CHUNK_SIZE / NOISE_HORIZONTAL_RESOLUTION);
        int startNoiseZ = chunkZ * (WorldConstants.CHUNK_SIZE / NOISE_HORIZONTAL_RESOLUTION);

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
                    double d000 = densityGrid[nx][ny][nz];
                    double d100 = densityGrid[nx + 1][ny][nz];
                    double d010 = densityGrid[nx][ny + 1][nz];
                    double d110 = densityGrid[nx + 1][ny + 1][nz];
                    double d001 = densityGrid[nx][ny][nz + 1];
                    double d101 = densityGrid[nx + 1][ny][nz + 1];
                    double d011 = densityGrid[nx][ny + 1][nz + 1];
                    double d111 = densityGrid[nx + 1][ny + 1][nz + 1];

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

                        double stepX0 = (yBase10 - yBase00) / NOISE_HORIZONTAL_RESOLUTION;
                        double stepX1 = (yBase11 - yBase01) / NOISE_HORIZONTAL_RESOLUTION;

                        double xBase0 = yBase00;
                        double xBase1 = yBase01;

                        for (int dx = 0; dx < NOISE_HORIZONTAL_RESOLUTION; dx++) {
                            int localX = nx * NOISE_HORIZONTAL_RESOLUTION + dx;
                            if (localX >= WorldConstants.CHUNK_SIZE) break;

                            double stepZ = (xBase1 - xBase0) / NOISE_HORIZONTAL_RESOLUTION;
                            double density = xBase0;

                            for (int dz = 0; dz < NOISE_HORIZONTAL_RESOLUTION; dz++) {
                                int localZ = nz * NOISE_HORIZONTAL_RESOLUTION + dz;
                                if (localZ >= WorldConstants.CHUNK_SIZE) break;

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
     * Uses GenConfig parameters (baseSize, stretchY, heightScale) to shape terrain.
     */
    private void sampleNoiseColumn(double[] column, int noiseX, int noiseZ) {
        double scaleVal = scaleNoise.sampleScaled2D(noiseX, noiseZ, 1.0, 0.0, 1.0);
        double depthVal = depthNoise.sampleScaled2D(noiseX, noiseZ, DEPTH_NOISE_SCALE_X, 0.0, DEPTH_NOISE_SCALE_Z);

        double scale = (scaleVal + 256.0) / 512.0;
        if (scale > 1.0) scale = 1.0;

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
            // INFDEV 611 CRITICAL: omitting depth /= 2.0 creates extreme terrain
            scale = 0.0;
        } else {
            if (depth > 1.0) {
                depth = 1.0;
            }
            depth /= 6.0;
        }

        scale += 0.5;
        depth = depth * baseSize / 8.0;
        depth = baseSize + depth * 4.0;

        for (int noiseY = 0; noiseY < NOISE_SIZE_Y; noiseY++) {
            double densityOffset = ((double) noiseY - depth) * stretchY / scale;
            if (densityOffset < 0.0) {
                densityOffset *= 4.0;
            }

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

            // Apply height scale multiplier from GenConfig
            // This amplifies or dampens the density, making terrain taller or flatter
            if (heightScale != 1.0) {
                density *= heightScale;
            }

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
     */
    public int getTerrainHeight(int worldX, int worldZ) {
        int noiseX = Math.floorDiv(worldX, NOISE_HORIZONTAL_RESOLUTION);
        int noiseZ = Math.floorDiv(worldZ, NOISE_HORIZONTAL_RESOLUTION);

        double[] column = new double[NOISE_SIZE_Y];
        sampleNoiseColumn(column, noiseX, noiseZ);

        for (int ny = NOISE_SIZE_Y - 2; ny >= 0; ny--) {
            int blockY = ny * NOISE_VERTICAL_RESOLUTION + NOISE_VERTICAL_RESOLUTION / 2;
            if (column[ny] > 0 && column[ny + 1] <= 0) {
                double ratio = column[ny] / (column[ny] - column[ny + 1]);
                return Math.min(blockY + (int)(ratio * NOISE_VERTICAL_RESOLUTION),
                    WorldConstants.WORLD_HEIGHT - 1);
            }
        }

        if (column[0] > 0) return WorldConstants.SEA_LEVEL;
        return 1;
    }

    public Infdev611OctaveNoise getBeachNoise() { return beachNoise; }
    public Infdev611OctaveNoise getSurfaceNoise() { return surfaceNoise; }
    public Infdev611OctaveNoise getForestNoise() { return forestNoise; }
}
