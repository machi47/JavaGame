package com.voxelgame.world.mesh;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldAccess;

/**
 * Abstract meshing interface. Defines the contract for converting
 * chunk block data into renderable vertex data.
 */
public interface Mesher {
    /**
     * Build meshes for the given chunk (includes GPU upload).
     * Returns a MeshResult containing separate opaque and transparent meshes.
     * MUST be called on the GL thread.
     *
     * @param chunk the chunk to mesh
     * @param world world access for neighbor lookups
     * @return a MeshResult with uploaded GPU data for both passes
     */
    MeshResult meshAll(Chunk chunk, WorldAccess world);

    /**
     * Build raw mesh data for the given chunk (CPU-only, no GL calls).
     * Safe to call from any thread.
     *
     * @param chunk the chunk to mesh
     * @param world world access for neighbor lookups
     * @return a RawMeshResult with vertex/index arrays
     */
    RawMeshResult meshAllRaw(Chunk chunk, WorldAccess world);

    /**
     * Build raw mesh data for all sections of a chunk (CPU-only, no GL calls).
     * This is the optimized path that skips empty sections.
     * Safe to call from any thread.
     *
     * @param chunk the chunk to mesh
     * @param world world access for neighbor lookups
     * @return a RawSectionMeshResult with per-section vertex/index arrays
     */
    default RawSectionMeshResult meshAllSectionsRaw(Chunk chunk, WorldAccess world) {
        // Default implementation: fall back to full chunk meshing for compatibility
        // Implementations should override this for optimal performance
        RawMeshResult fullMesh = meshAllRaw(chunk, world);
        RawSectionMeshResult result = new RawSectionMeshResult();
        // Put all data in section 0 as fallback
        result.setSection(0, fullMesh.opaqueData(), fullMesh.transparentData());
        return result;
    }
}
