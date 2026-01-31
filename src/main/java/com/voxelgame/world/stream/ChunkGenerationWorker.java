package com.voxelgame.world.stream;

/**
 * Background worker thread for chunk generation. Pulls generation tasks
 * from the queue and runs the GenPipeline to produce chunk data.
 */
public class ChunkGenerationWorker implements Runnable {
    @Override
    public void run() {
        // TODO: pull from queue, run GenPipeline, deliver result
    }
}
