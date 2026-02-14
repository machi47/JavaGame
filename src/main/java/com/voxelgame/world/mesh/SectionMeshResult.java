package com.voxelgame.world.mesh;

import com.voxelgame.world.WorldConstants;

/**
 * GPU-resident mesh result for all 8 vertical sections.
 * Each section is 16×16×16 blocks. Empty sections have null meshes.
 */
public class SectionMeshResult {

    private final ChunkMesh[] opaqueMeshes;
    private final ChunkMesh[] transparentMeshes;

    public SectionMeshResult() {
        this.opaqueMeshes = new ChunkMesh[WorldConstants.SECTIONS_PER_CHUNK];
        this.transparentMeshes = new ChunkMesh[WorldConstants.SECTIONS_PER_CHUNK];
    }

    public void setSection(int sectionIndex, ChunkMesh opaque, ChunkMesh transparent) {
        if (sectionIndex >= 0 && sectionIndex < WorldConstants.SECTIONS_PER_CHUNK) {
            opaqueMeshes[sectionIndex] = opaque;
            transparentMeshes[sectionIndex] = transparent;
        }
    }

    public ChunkMesh getOpaqueMesh(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= WorldConstants.SECTIONS_PER_CHUNK) return null;
        return opaqueMeshes[sectionIndex];
    }

    public ChunkMesh getTransparentMesh(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= WorldConstants.SECTIONS_PER_CHUNK) return null;
        return transparentMeshes[sectionIndex];
    }

    public ChunkMesh[] getOpaqueMeshes() {
        return opaqueMeshes;
    }

    public ChunkMesh[] getTransparentMeshes() {
        return transparentMeshes;
    }
}
