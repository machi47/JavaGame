package com.voxelgame.world.mesh;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldAccess;

/**
 * Abstract meshing interface. Defines the contract for converting
 * chunk block data into renderable vertex data.
 */
public interface Mesher {
    /**
     * Build meshes for the given chunk.
     * Returns a MeshResult containing separate opaque and transparent meshes.
     *
     * @param chunk the chunk to mesh
     * @param world world access for neighbor lookups
     * @return a MeshResult with uploaded GPU data for both passes
     */
    MeshResult meshAll(Chunk chunk, WorldAccess world);
}
