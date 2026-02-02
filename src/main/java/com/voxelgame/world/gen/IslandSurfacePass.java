package com.voxelgame.world.gen;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

import java.util.Random;

/**
 * Surface painting for floating islands.
 * Similar to Infdev611SurfacePass but:
 * - No water filling (void world)
 * - No beach rules
 * - Grass/dirt on top surfaces, bedrock not placed (islands float)
 * - Dirt depth is thinner (islands are smaller)
 */
public class IslandSurfacePass implements GenPipeline.GenerationPass {

    private final long seed;

    public IslandSurfacePass(long seed) {
        this.seed = seed;
    }

    @Override
    public void apply(Chunk chunk, GenContext context) {
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();
        Random rand = new Random((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L + seed);

        for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < WorldConstants.CHUNK_SIZE; lz++) {
                int surfaceDepth = 2 + rand.nextInt(3); // 2-4 dirt layers
                int runDepth = -1;

                // Scan top-down to find and paint surfaces
                for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
                    int block = chunk.getBlock(lx, y, lz);

                    if (block == Blocks.AIR.id()) {
                        runDepth = -1;
                    } else if (block == Blocks.STONE.id()) {
                        if (runDepth == -1) {
                            // Found surface — paint grass on top
                            runDepth = surfaceDepth;
                            chunk.setBlock(lx, y, lz, Blocks.GRASS.id());
                        } else if (runDepth > 0) {
                            // Below surface — place dirt
                            runDepth--;
                            chunk.setBlock(lx, y, lz, Blocks.DIRT.id());
                        }
                        // After surfaceDepth layers, leave as stone
                    }
                }
            }
        }
    }
}
