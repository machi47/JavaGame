package com.voxelgame.world.gen;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Superflat world generation pass.
 * Places layers of blocks defined in GenConfig.flatLayers from y=0 upward.
 * Default layers: 1 bedrock, 3 stone, 8 dirt, 1 grass.
 * Everything above the layers is air.
 */
public class FlatWorldPass implements GenPipeline.GenerationPass {

    @Override
    public void apply(Chunk chunk, GenContext context) {
        GenConfig config = context.getConfig();

        // Determine layers
        int[] layers = config.flatLayers;
        if (layers == null || layers.length == 0) {
            // Default superflat: bedrock, 3 stone, 8 dirt, grass
            layers = new int[]{
                Blocks.BEDROCK.id(),
                Blocks.STONE.id(), Blocks.STONE.id(), Blocks.STONE.id(),
                Blocks.DIRT.id(), Blocks.DIRT.id(), Blocks.DIRT.id(), Blocks.DIRT.id(),
                Blocks.DIRT.id(), Blocks.DIRT.id(), Blocks.DIRT.id(), Blocks.DIRT.id(),
                Blocks.GRASS.id()
            };
        }

        for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < WorldConstants.CHUNK_SIZE; lz++) {
                for (int y = 0; y < WorldConstants.WORLD_HEIGHT; y++) {
                    if (y < layers.length) {
                        chunk.setBlock(lx, y, lz, layers[y]);
                    } else {
                        chunk.setBlock(lx, y, lz, Blocks.AIR.id());
                    }
                }
            }
        }
    }
}
