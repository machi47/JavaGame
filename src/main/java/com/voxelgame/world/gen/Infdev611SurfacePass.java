package com.voxelgame.world.gen;

import com.voxelgame.math.Infdev611OctaveNoise;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

import java.util.Random;

/**
 * Authentic Infdev 611 surface painting pass.
 *
 * Works with the 3D density terrain (Infdev611TerrainPass):
 * - Scans each column top-down
 * - Replaces stone at the surface with grass/dirt
 * - Applies sand/gravel beaches near sea level using beachOctaveNoise
 * - Places randomized jagged bedrock at the bottom
 * - Fills water in air below sea level
 *
 * The surface rules are applied per-column, finding the first stone block
 * from the top and painting downward.
 */
public class Infdev611SurfacePass implements GenPipeline.GenerationPass {

    private static final double BEACH_SCALE = 0.03125; // 1/32

    private final Infdev611OctaveNoise beachNoise;
    private final Infdev611OctaveNoise surfaceNoise;
    private final long seed;

    public Infdev611SurfacePass(Infdev611OctaveNoise beachNoise,
                                 Infdev611OctaveNoise surfaceNoise,
                                 long seed) {
        this.beachNoise = beachNoise;
        this.surfaceNoise = surfaceNoise;
        this.seed = seed;
    }

    @Override
    public void apply(Chunk chunk, GenContext context) {
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();
        int chunkWorldX = chunk.getPos().worldX();
        int chunkWorldZ = chunk.getPos().worldZ();

        // Use configurable sea level from GenConfig
        int seaLevel = context.getConfig().seaLevel;

        Random rand = new Random((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L + seed);
        Random bedrockRand = new Random((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L + seed);

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkWorldX + localX;
                int worldZ = chunkWorldZ + localZ;

                boolean genSandBeach = beachNoise.sample(
                    worldX * BEACH_SCALE,
                    worldZ * BEACH_SCALE,
                    0.0
                ) + rand.nextDouble() * 0.2 > 0.0;

                boolean genGravelBeach = beachNoise.sample(
                    worldZ * BEACH_SCALE,
                    109.0134,
                    worldX * BEACH_SCALE
                ) + rand.nextDouble() * 0.2 > 3.0;

                int surfaceDepth = (int)(surfaceNoise.sample2D(
                    worldX * BEACH_SCALE * 2.0,
                    worldZ * BEACH_SCALE * 2.0
                ) / 3.0 + 3.0 + rand.nextDouble() * 0.25);

                int runDepth = -1;

                int topBlock = Blocks.GRASS.id();
                int fillerBlock = Blocks.DIRT.id();

                for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
                    if (y <= 0 + bedrockRand.nextInt(5)) {
                        chunk.setBlock(localX, y, localZ, Blocks.BEDROCK.id());
                        continue;
                    }

                    int currentBlock = chunk.getBlock(localX, y, localZ);

                    if (currentBlock == Blocks.AIR.id()) {
                        runDepth = -1;

                        // Fill water below configurable sea level
                        if (y <= seaLevel) {
                            chunk.setBlock(localX, y, localZ, Blocks.WATER.id());
                        }
                    } else if (currentBlock == Blocks.STONE.id()) {
                        if (runDepth == -1) {
                            if (surfaceDepth <= 0) {
                                topBlock = Blocks.AIR.id();
                                fillerBlock = Blocks.STONE.id();
                            } else if (y >= seaLevel - 4 &&
                                       y <= seaLevel + 1) {
                                topBlock = Blocks.GRASS.id();
                                fillerBlock = Blocks.DIRT.id();

                                if (genGravelBeach) {
                                    topBlock = Blocks.GRAVEL.id();
                                    fillerBlock = Blocks.GRAVEL.id();
                                }

                                if (genSandBeach) {
                                    topBlock = Blocks.SAND.id();
                                    fillerBlock = Blocks.SAND.id();
                                }
                            }

                            runDepth = surfaceDepth;

                            if (y < seaLevel && topBlock == Blocks.AIR.id()) {
                                topBlock = Blocks.WATER.id();
                            }

                            if (y >= seaLevel - 1) {
                                chunk.setBlock(localX, y, localZ, topBlock);
                            } else {
                                chunk.setBlock(localX, y, localZ, fillerBlock);
                            }
                        } else if (runDepth > 0) {
                            runDepth--;
                            chunk.setBlock(localX, y, localZ, fillerBlock);
                        }
                    }
                }

                topBlock = Blocks.GRASS.id();
                fillerBlock = Blocks.DIRT.id();
            }
        }
    }
}
