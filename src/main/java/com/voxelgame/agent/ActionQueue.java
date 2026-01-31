package com.voxelgame.agent;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Thread-safe action buffer for agent commands.
 * <p>
 * WebSocket threads enqueue actions; the game loop's Controller drains
 * them each tick. Actions are atomic — they execute exactly once,
 * same priority as keyboard input.
 */
public class ActionQueue {

    /**
     * A single agent action with type tag and parameters.
     */
    public record AgentAction(
        String type,
        float param1,   // primary axis or value
        float param2,   // secondary axis or value
        long durationMs, // for timed actions (move duration, etc.)
        int intParam,    // for slot selection, etc.
        String sourceId  // which agent connection sent this
    ) {
        // Convenience constructors
        public static AgentAction look(float yawDelta, float pitchDelta, String src) {
            return new AgentAction("action_look", yawDelta, pitchDelta, 0, 0, src);
        }

        public static AgentAction move(float forward, float strafe, long durationMs, String src) {
            return new AgentAction("action_move", forward, strafe, durationMs, 0, src);
        }

        public static AgentAction jump(String src) {
            return new AgentAction("action_jump", 0, 0, 0, 0, src);
        }

        public static AgentAction crouch(boolean toggle, String src) {
            return new AgentAction("action_crouch", toggle ? 1.0f : 0.0f, 0, 0, 0, src);
        }

        public static AgentAction sprint(boolean toggle, String src) {
            return new AgentAction("action_sprint", toggle ? 1.0f : 0.0f, 0, 0, 0, src);
        }

        public static AgentAction use(String src) {
            return new AgentAction("action_use", 0, 0, 0, 0, src);
        }

        public static AgentAction attack(String src) {
            return new AgentAction("action_attack", 0, 0, 0, 0, src);
        }

        public static AgentAction hotbarSelect(int slot, String src) {
            return new AgentAction("action_hotbar_select", 0, 0, 0, slot, src);
        }
    }

    private final ConcurrentLinkedQueue<AgentAction> queue = new ConcurrentLinkedQueue<>();
    private final AtomicLong totalEnqueued = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);

    /**
     * Enqueue an action from an agent. Thread-safe.
     */
    public void enqueue(AgentAction action) {
        queue.add(action);
        totalEnqueued.incrementAndGet();
    }

    /**
     * Drain all queued actions, passing each to the consumer.
     * Called once per game tick from the main thread.
     */
    public void drain(Consumer<AgentAction> consumer) {
        AgentAction action;
        while ((action = queue.poll()) != null) {
            consumer.accept(action);
            totalProcessed.incrementAndGet();
        }
    }

    /**
     * Check if there are pending actions.
     */
    public boolean hasPending() {
        return !queue.isEmpty();
    }

    /**
     * Get total actions enqueued (for stats).
     */
    public long getTotalEnqueued() {
        return totalEnqueued.get();
    }

    /**
     * Get total actions processed (for stats).
     */
    public long getTotalProcessed() {
        return totalProcessed.get();
    }

    /**
     * Clear all pending actions (e.g., on agent disconnect).
     */
    public void clear() {
        queue.clear();
    }
}
