package com.voxelgame.world.mesh;

/**
 * Container for the two mesh passes produced by meshing a chunk:
 * an opaque (solid) mesh and a transparent (water, etc.) mesh.
 */
public record MeshResult(ChunkMesh opaqueMesh, ChunkMesh transparentMesh) {
}
