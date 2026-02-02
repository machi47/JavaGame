package com.voxelgame.world.gen;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Fluid filling pass. Places water in all air blocks at or below sea level.
 * Uses configurable sea level from GenConfig.
 * Skipped for flat worlds and floating islands (no oceans).
 */
public class FillFluidsPass implements GenPipeline.GenerationPass {

    @Override
    public void apply(Chunk chunk, GenContext context) {
        GenConfig config = context.getConfig();

        // No fluid filling for flat worlds or floating islands
        if (config.flatWorld || config.floatingIslands) return;

        int seaLevel = config.seaLevel;

        for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < WorldConstants.CHUNK_SIZE; lz++) {
                for (int y = seaLevel; y >= 1; y--) {
                    int block = chunk.getBlock(lx, y, lz);

                    if (block == Blocks.AIR.id()) {
                        chunk.setBlock(lx, y, lz, Blocks.WATER.id());
                    } else if (block != Blocks.WATER.id()) {
                        break;
                    }
                }
            }
        }
    }
}
