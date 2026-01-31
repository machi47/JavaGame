package com.voxelgame.world.stream;

import com.voxelgame.world.Chunk;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Background worker thread for chunk generation. Pulls tasks from the
 * queue and generates flat world chunks.
 */
public class ChunkGenerationWorker implements Runnable {

    private final BlockingQueue<ChunkTask> taskQueue;
    private final ConcurrentLinkedQueue<ChunkTask> completedQueue;
    private volatile boolean running = true;

    public ChunkGenerationWorker(BlockingQueue<ChunkTask> taskQueue,
                                  ConcurrentLinkedQueue<ChunkTask> completedQueue) {
        this.taskQueue = taskQueue;
        this.completedQueue = completedQueue;
    }

    @Override
    public void run() {
        while (running) {
            try {
                ChunkTask task = taskQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (task == null) continue;

                // Generate the chunk (flat world for now)
                Chunk chunk = new Chunk(task.getPos());
                chunk.generateFlat();
                task.setResult(chunk);
                completedQueue.add(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stop() {
        running = false;
    }
}
