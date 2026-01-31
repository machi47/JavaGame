package com.voxelgame.world.mesh;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldAccess;

/**
 * Abstract meshing interface. Defines the contract for converting
 * chunk block data into renderable vertex data.
 */
public interface Mesher {
    /**
     * Build a mesh for the given chunk.
     * @param chunk the chunk to mesh
     * @param world world access for neighbor lookups
     * @return a ChunkMesh with uploaded GPU data
     */
    ChunkMesh mesh(Chunk chunk, WorldAccess world);
}
