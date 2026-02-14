package com.voxelgame.world.mesh;

import com.voxelgame.world.WorldConstants;

/**
 * CPU-side mesh result with raw vertex/index data for all 8 vertical sections.
 * Used for background meshing — no GL calls involved.
 *
 * Each section is 16×16×16 blocks. Empty sections have null MeshData.
 */
public class RawSectionMeshResult {

    private final MeshData[] opaqueSections;
    private final MeshData[] transparentSections;

    public RawSectionMeshResult() {
        this.opaqueSections = new MeshData[WorldConstants.SECTIONS_PER_CHUNK];
        this.transparentSections = new MeshData[WorldConstants.SECTIONS_PER_CHUNK];
    }

    public void setSection(int sectionIndex, MeshData opaque, MeshData transparent) {
        if (sectionIndex >= 0 && sectionIndex < WorldConstants.SECTIONS_PER_CHUNK) {
            opaqueSections[sectionIndex] = opaque;
            transparentSections[sectionIndex] = transparent;
        }
    }

    public MeshData getOpaqueSection(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= WorldConstants.SECTIONS_PER_CHUNK) return null;
        return opaqueSections[sectionIndex];
    }

    public MeshData getTransparentSection(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= WorldConstants.SECTIONS_PER_CHUNK) return null;
        return transparentSections[sectionIndex];
    }

    /**
     * Convert to GPU-resident SectionMeshResult (must call on GL thread).
     */
    public SectionMeshResult upload() {
        SectionMeshResult result = new SectionMeshResult();
        for (int i = 0; i < WorldConstants.SECTIONS_PER_CHUNK; i++) {
            ChunkMesh opaque = opaqueSections[i] != null ? opaqueSections[i].toChunkMesh() : null;
            ChunkMesh transparent = transparentSections[i] != null ? transparentSections[i].toChunkMesh() : null;
            result.setSection(i, opaque, transparent);
        }
        return result;
    }

    /**
     * Check if any section has mesh data.
     */
    public boolean hasAnyMesh() {
        for (int i = 0; i < WorldConstants.SECTIONS_PER_CHUNK; i++) {
            if (opaqueSections[i] != null || transparentSections[i] != null) {
                return true;
            }
        }
        return false;
    }
}
