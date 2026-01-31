package com.voxelgame.world.mesh;

/**
 * Simple per-face mesher. Emits two triangles for every visible block face.
 * Easy to implement, used as baseline before greedy meshing.
 */
public class NaiveMesher implements Mesher {
    // TODO: iterate blocks, cull hidden faces, emit quads
}
