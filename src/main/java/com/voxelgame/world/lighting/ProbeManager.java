package com.voxelgame.world.lighting;

import com.voxelgame.render.SkySystem;
import com.voxelgame.world.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages irradiance probe grids across all loaded chunks.
 * 
 * Responsibilities:
 * - Create/destroy probe grids when chunks load/unload
 * - Amortize probe updates across frames (budget-limited)
 * - Trigger updates on time-of-day changes and block modifications
 * - Provide sampling interface for meshers
 * 
 * Performance:
 * - Only update 1-4 chunk grids per frame maximum
 * - Priority queue: closer chunks update first
 * - Block changes only update nearby probes, not entire grid
 */
public class ProbeManager {

    /** Maximum probe grid updates per frame */
    private static final int MAX_UPDATES_PER_FRAME = 2;
    
    /** Time-of-day change threshold to trigger full update (in 0-1 scale) */
    private static final float TIME_CHANGE_THRESHOLD = 0.02f; // ~30 game minutes
    
    /** All probe grids by chunk position */
    private final Map<ChunkPos, IrradianceProbeGrid> grids = new ConcurrentHashMap<>();
    
    /** Priority queue of dirty grids needing update */
    private final PriorityQueue<ChunkPos> updateQueue = new PriorityQueue<>(
        Comparator.comparingInt(pos -> -getGridPriority(pos))
    );
    
    /** Set of positions in the queue (for O(1) contains check) */
    private final Set<ChunkPos> inQueue = new HashSet<>();
    
    /** Shared sky system reference */
    private SkySystem skySystem;
    
    /** Last known time of day (for detecting significant changes) */
    private float lastTimeOfDay = -1;
    
    /** Player chunk position (for priority calculation) */
    private int playerChunkX = 0;
    private int playerChunkZ = 0;
    
    /**
     * Initialize the probe manager.
     * 
     * @param skySystem The sky system for lighting calculations
     */
    public void init(SkySystem skySystem) {
        this.skySystem = skySystem;
    }
    
    /**
     * Called when a chunk is loaded/generated.
     * Creates a probe grid for the chunk and marks it for update.
     */
    public void onChunkLoaded(Chunk chunk, WorldAccess world, float timeOfDay) {
        ChunkPos pos = chunk.getPos();
        
        // Create new probe grid
        IrradianceProbeGrid grid = new IrradianceProbeGrid(pos.x(), pos.z());
        grids.put(pos, grid);
        
        // Queue for update
        queueUpdate(pos);
    }
    
    /**
     * Called when a chunk is unloaded.
     */
    public void onChunkUnloaded(ChunkPos pos) {
        grids.remove(pos);
        synchronized (updateQueue) {
            if (inQueue.remove(pos)) {
                updateQueue.remove(pos);
            }
        }
    }
    
    /**
     * Called when a block is placed or removed.
     * Updates only the probes near the change.
     */
    public void onBlockChanged(int worldX, int worldY, int worldZ, 
                                WorldAccess world, float timeOfDay) {
        // Find the chunk containing this block
        int cx = Math.floorDiv(worldX, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(worldZ, WorldConstants.CHUNK_SIZE);
        ChunkPos pos = new ChunkPos(cx, cz);
        
        IrradianceProbeGrid grid = grids.get(pos);
        if (grid != null && skySystem != null) {
            // Update only nearby probes (not the entire grid)
            grid.updateProbesNear(worldX, worldY, worldZ, world, skySystem, timeOfDay);
        }
        
        // Also update neighboring chunks if the block is near a boundary
        int localX = Math.floorMod(worldX, WorldConstants.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, WorldConstants.CHUNK_SIZE);
        
        // Within 4 blocks of boundary = might affect neighbor probes
        if (localX < 4) updateProbesInChunk(cx - 1, cz, worldX, worldY, worldZ, world, timeOfDay);
        if (localX >= 12) updateProbesInChunk(cx + 1, cz, worldX, worldY, worldZ, world, timeOfDay);
        if (localZ < 4) updateProbesInChunk(cx, cz - 1, worldX, worldY, worldZ, world, timeOfDay);
        if (localZ >= 12) updateProbesInChunk(cx, cz + 1, worldX, worldY, worldZ, world, timeOfDay);
    }
    
    private void updateProbesInChunk(int cx, int cz, int worldX, int worldY, int worldZ,
                                      WorldAccess world, float timeOfDay) {
        ChunkPos pos = new ChunkPos(cx, cz);
        IrradianceProbeGrid grid = grids.get(pos);
        if (grid != null && skySystem != null) {
            grid.updateProbesNear(worldX, worldY, worldZ, world, skySystem, timeOfDay);
        }
    }
    
    /**
     * Called when time of day changes significantly.
     * Marks all grids as dirty for gradual re-update.
     */
    public void onTimeOfDayChanged(float timeOfDay, WorldAccess world) {
        // Check if change is significant
        if (lastTimeOfDay >= 0) {
            float delta = Math.abs(timeOfDay - lastTimeOfDay);
            // Handle wrap-around (0.99 -> 0.01)
            if (delta > 0.5f) delta = 1.0f - delta;
            
            if (delta < TIME_CHANGE_THRESHOLD) {
                return; // Not significant enough
            }
        }
        
        lastTimeOfDay = timeOfDay;
        
        // Mark all grids as dirty
        for (Map.Entry<ChunkPos, IrradianceProbeGrid> entry : grids.entrySet()) {
            entry.getValue().markDirty();
            queueUpdate(entry.getKey());
        }
    }
    
    /**
     * Update player position (for priority calculation).
     */
    public void updatePlayerPosition(float worldX, float worldZ) {
        this.playerChunkX = (int) Math.floor(worldX / WorldConstants.CHUNK_SIZE);
        this.playerChunkZ = (int) Math.floor(worldZ / WorldConstants.CHUNK_SIZE);
    }
    
    /**
     * Perform amortized probe updates.
     * Call once per frame from the game loop.
     * 
     * @param world World access for block queries
     * @param timeOfDay Current time of day (0-1)
     */
    public void updateDirtyProbes(WorldAccess world, float timeOfDay) {
        if (skySystem == null) return;
        
        int updated = 0;
        
        synchronized (updateQueue) {
            // Re-prioritize queue based on current player position
            List<ChunkPos> toRequeue = new ArrayList<>(updateQueue);
            updateQueue.clear();
            inQueue.clear();
            
            for (ChunkPos pos : toRequeue) {
                IrradianceProbeGrid grid = grids.get(pos);
                if (grid != null && grid.isDirty()) {
                    grid.setUpdatePriority(calculatePriority(pos));
                    updateQueue.add(pos);
                    inQueue.add(pos);
                }
            }
            
            // Process up to MAX_UPDATES_PER_FRAME grids
            while (!updateQueue.isEmpty() && updated < MAX_UPDATES_PER_FRAME) {
                ChunkPos pos = updateQueue.poll();
                inQueue.remove(pos);
                
                IrradianceProbeGrid grid = grids.get(pos);
                if (grid != null) {
                    grid.updateAllProbes(world, skySystem, timeOfDay);
                    updated++;
                }
            }
        }
    }
    
    /**
     * Sample indirect lighting at a world position.
     * Returns RGB irradiance interpolated from nearby probes.
     * 
     * @param worldX World X coordinate
     * @param worldY World Y coordinate
     * @param worldZ World Z coordinate
     * @return RGB irradiance [r, g, b], or zero if no grid available
     */
    public float[] sampleIndirect(float worldX, float worldY, float worldZ) {
        int cx = (int) Math.floor(worldX / WorldConstants.CHUNK_SIZE);
        int cz = (int) Math.floor(worldZ / WorldConstants.CHUNK_SIZE);
        
        IrradianceProbeGrid grid = grids.get(new ChunkPos(cx, cz));
        if (grid == null) {
            return new float[] {0, 0, 0};
        }
        
        // Convert to local chunk coordinates
        float localX = worldX - cx * WorldConstants.CHUNK_SIZE;
        float localZ = worldZ - cz * WorldConstants.CHUNK_SIZE;
        
        return grid.sampleAt(localX, worldY, localZ);
    }
    
    /**
     * Check if a chunk has a probe grid.
     */
    public boolean hasGrid(int cx, int cz) {
        return grids.containsKey(new ChunkPos(cx, cz));
    }
    
    /**
     * Get the probe grid for a chunk (for debugging).
     */
    public IrradianceProbeGrid getGrid(int cx, int cz) {
        return grids.get(new ChunkPos(cx, cz));
    }
    
    /**
     * Get the number of loaded grids.
     */
    public int getGridCount() {
        return grids.size();
    }
    
    /**
     * Get the number of grids pending update.
     */
    public int getPendingUpdateCount() {
        return updateQueue.size();
    }
    
    /**
     * Add a chunk position to the update queue if not already present.
     */
    private void queueUpdate(ChunkPos pos) {
        synchronized (updateQueue) {
            if (!inQueue.contains(pos)) {
                IrradianceProbeGrid grid = grids.get(pos);
                if (grid != null) {
                    grid.setUpdatePriority(calculatePriority(pos));
                    grid.markDirty();
                    updateQueue.add(pos);
                    inQueue.add(pos);
                }
            }
        }
    }
    
    /**
     * Calculate update priority for a chunk.
     * Higher = update sooner. Based on distance to player.
     */
    private int calculatePriority(ChunkPos pos) {
        int dx = pos.x() - playerChunkX;
        int dz = pos.z() - playerChunkZ;
        int distSq = dx * dx + dz * dz;
        // Invert so closer chunks have higher priority
        return 1000 - Math.min(distSq, 1000);
    }
    
    /**
     * Get priority for a chunk (used by queue comparator).
     */
    private int getGridPriority(ChunkPos pos) {
        IrradianceProbeGrid grid = grids.get(pos);
        return grid != null ? grid.getUpdatePriority() : 0;
    }
    
    /**
     * Clear all grids (for world unload).
     */
    public void clear() {
        grids.clear();
        synchronized (updateQueue) {
            updateQueue.clear();
            inQueue.clear();
        }
        lastTimeOfDay = -1;
    }
}
