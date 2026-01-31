package com.voxelgame.world.stream;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.ChunkPos;

/**
 * Wraps a chunk generation request and its result.
 */
public class ChunkTask {

    private final ChunkPos pos;
    private volatile Chunk result;
    private volatile boolean done;

    public ChunkTask(ChunkPos pos) {
        this.pos = pos;
    }

    public ChunkPos getPos() { return pos; }

    public Chunk getResult() { return result; }
    public void setResult(Chunk chunk) {
        this.result = chunk;
        this.done = true;
    }

    public boolean isDone() { return done; }
}
