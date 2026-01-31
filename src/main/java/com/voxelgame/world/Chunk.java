package com.voxelgame.world;

import com.voxelgame.world.mesh.ChunkMesh;

/**
 * A 16×128×16 column of blocks.
 */
public class Chunk {

    private final ChunkPos pos;
    private final byte[] blocks;
    private boolean dirty = true;
    private ChunkMesh mesh;

    public Chunk(ChunkPos pos) {
        this.pos = pos;
        this.blocks = new byte[WorldConstants.CHUNK_VOLUME];
    }

    private int index(int x, int y, int z) {
        return (y * WorldConstants.CHUNK_SIZE * WorldConstants.CHUNK_SIZE) + (z * WorldConstants.CHUNK_SIZE) + x;
    }

    public int getBlock(int x, int y, int z) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return 0; // AIR for out of bounds
        }
        return blocks[index(x, y, z)] & 0xFF;
    }

    public void setBlock(int x, int y, int z, int blockId) {
        if (x < 0 || x >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT ||
            z < 0 || z >= WorldConstants.CHUNK_SIZE) {
            return;
        }
        blocks[index(x, y, z)] = (byte) blockId;
        dirty = true;
    }

    public boolean isDirty() { return dirty; }
    public void setDirty(boolean d) { this.dirty = d; }
    public ChunkPos getPos() { return pos; }

    public ChunkMesh getMesh() { return mesh; }
    public void setMesh(ChunkMesh mesh) {
        if (this.mesh != null) {
            this.mesh.dispose();
        }
        this.mesh = mesh;
    }

    public void dispose() {
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
    }

    /** Fill with flat world terrain for testing. */
    public void generateFlat() {
        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                for (int y = 0; y < WorldConstants.WORLD_HEIGHT; y++) {
                    int blockId;
                    if (y < 60) {
                        blockId = 1; // STONE
                    } else if (y < 64) {
                        blockId = 3; // DIRT
                    } else if (y == 64) {
                        blockId = 4; // GRASS
                    } else {
                        blockId = 0; // AIR
                    }
                    blocks[index(x, y, z)] = (byte) blockId;
                }
            }
        }
        dirty = true;
    }
}
