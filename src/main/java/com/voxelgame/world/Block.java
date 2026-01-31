package com.voxelgame.world;

/** Block type definition — id, name, properties, and per-face texture indices. */
public record Block(int id, String name, boolean solid, boolean transparent, int[] faceTextures) {

    /**
     * Face texture array order: [top, bottom, north, south, east, west].
     * A single-element array means all faces use the same texture index.
     */
    public int getTextureIndex(int face) {
        if (faceTextures.length == 1) return faceTextures[0];
        return faceTextures[face];
    }
}
