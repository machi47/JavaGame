package com.voxelgame.world.gen;

import com.voxelgame.math.Infdev611OctaveNoise;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

import java.util.Random;

/**
 * Floating Islands terrain generation.
 * Creates sky islands suspended in the air with void below.
 *
 * Algorithm:
 * 1. Sample 3D noise at each position
 * 2. Apply a Y-based "island band" modifier that favors islands
 *    in the mid-height range (islandMinY to islandMaxY)
 * 3. Use a secondary noise for island separation (large scale)
 * 4. Density > threshold = stone, else = air (no water anywhere)
 *
 * The result: organic floating landmasses at various heights,
 * connected by nothing, with void below and sky above.
 */
public class FloatingIslandsPass implements GenPipeline.GenerationPass {

    private final Infdev611OctaveNoise primaryNoise;   // 3D island shape
    private final Infdev611OctaveNoise secondaryNoise; // Island separation
    private final Infdev611OctaveNoise detailNoise;    // Surface detail
    private final GenConfig config;

    public FloatingIslandsPass(long seed, GenConfig config) {
        this.config = config;
        Random random = new Random(seed + 77777L);
        this.primaryNoise = new Infdev611OctaveNoise(random, 12);
        this.secondaryNoise = new Infdev611OctaveNoise(random, 6);
        this.detailNoise = new Infdev611OctaveNoise(random, 4);
    }

    @Override
    public void apply(Chunk chunk, GenContext context) {
        int chunkWorldX = chunk.getPos().worldX();
        int chunkWorldZ = chunk.getPos().worldZ();

        int minY = config.islandMinY;
        int maxY = config.islandMaxY;
        double threshold = config.islandDensityThreshold;

        for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < WorldConstants.CHUNK_SIZE; lz++) {
                int worldX = chunkWorldX + lx;
                int worldZ = chunkWorldZ + lz;

                for (int y = 0; y < WorldConstants.WORLD_HEIGHT; y++) {
                    // Everything below island band is void (air)
                    if (y < minY - 10 || y > maxY + 10) {
                        chunk.setBlock(lx, y, lz, Blocks.AIR.id());
                        continue;
                    }

                    // Bedrock at y=0 as safety net
                    if (y == 0) {
                        chunk.setBlock(lx, y, lz, Blocks.AIR.id());
                        continue;
                    }

                    // Sample 3D noise for island shape
                    double freqXZ = 0.008;  // horizontal scale
                    double freqY = 0.015;   // vertical scale (flatter islands)
                    double primary = primaryNoise.sample(
                        worldX * freqXZ,
                        y * freqY,
                        worldZ * freqXZ
                    );

                    // Secondary noise for island separation (large blobs)
                    double secondary = secondaryNoise.sample(
                        worldX * 0.003,
                        y * 0.006,
                        worldZ * 0.003
                    );

                    // Y-based band modifier: strongest in the middle, fading at edges
                    double bandCenter = (minY + maxY) / 2.0;
                    double bandRadius = (maxY - minY) / 2.0;
                    double distFromCenter = Math.abs(y - bandCenter) / bandRadius;
                    double bandFactor = 1.0 - distFromCenter * distFromCenter;
                    bandFactor = Math.max(0, bandFactor);

                    // Combine: primary shape + separation + band modifier
                    double density = primary * 0.6 + secondary * 0.4;
                    density *= bandFactor;

                    // Add slight detail noise for surface variation
                    double detail = detailNoise.sample2D(
                        worldX * 0.05, worldZ * 0.05
                    ) * 0.03;
                    density += detail;

                    if (density > threshold) {
                        chunk.setBlock(lx, y, lz, Blocks.STONE.id());
                    } else {
                        chunk.setBlock(lx, y, lz, Blocks.AIR.id());
                    }
                }
            }
        }
    }
}
