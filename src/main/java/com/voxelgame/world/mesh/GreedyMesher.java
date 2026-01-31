package com.voxelgame.world.mesh;

/**
 * Greedy meshing algorithm. Merges adjacent coplanar faces of the same
 * block type into larger quads, dramatically reducing vertex count.
 */
public class GreedyMesher implements Mesher {
    // TODO: slice-based greedy merge per axis
}
