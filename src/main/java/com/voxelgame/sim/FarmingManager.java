package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Farming system manager. Implements random tick system for crop growth.
 *
 * Each game tick (called from GameLoop), a random selection of blocks in
 * loaded chunks near the player get "random ticked". Wheat crops advance
 * through growth stages 0→7 over time.
 *
 * Hydration bonus: farmland within 4 blocks of water grows crops 2× faster.
 */
public class FarmingManager {

    private static final float TICK_INTERVAL = 1.0f; // seconds between random tick batches
    private static final int RANDOM_TICKS_PER_CHUNK = 3; // blocks per chunk per tick (like Minecraft)
    private static final int TICK_RADIUS = 4; // chunks around player to tick
    private static final int WATER_SEARCH_RADIUS = 4; // blocks to search for water hydration

    // Base growth chance per random tick (without hydration)
    // With ~3 random ticks per chunk per second, and 16x256x16=65536 blocks per chunk,
    // each specific block gets ticked roughly every 65536/3 ≈ 21845 seconds.
    // So we make the growth chance high per tick to compensate: ~33% chance per tick
    private static final float BASE_GROWTH_CHANCE = 0.33f;
    private static final float HYDRATED_GROWTH_MULTIPLIER = 2.0f;

    private final Random random = new Random();
    private float tickTimer = 0;

    // Track which chunks were affected for mesh rebuilds
    private final Set<ChunkPos> dirtyChunks = new HashSet<>();

    /**
     * Update the farming system. Called every frame from GameLoop.
     * @param dt delta time
     * @param world the game world
     * @param playerX player X position (for chunk proximity)
     * @param playerZ player Z position (for chunk proximity)
     * @return set of chunk positions that need mesh rebuilds, or empty
     */
    public Set<ChunkPos> update(float dt, World world, float playerX, float playerZ) {
        dirtyChunks.clear();
        tickTimer += dt;

        if (tickTimer < TICK_INTERVAL) {
            return dirtyChunks;
        }
        tickTimer -= TICK_INTERVAL;

        // Get player chunk coordinates
        int pcx = Math.floorDiv((int) playerX, WorldConstants.CHUNK_SIZE);
        int pcz = Math.floorDiv((int) playerZ, WorldConstants.CHUNK_SIZE);

        // Random tick chunks near the player
        for (int cx = pcx - TICK_RADIUS; cx <= pcx + TICK_RADIUS; cx++) {
            for (int cz = pcz - TICK_RADIUS; cz <= pcz + TICK_RADIUS; cz++) {
                Chunk chunk = world.getChunk(cx, cz);
                if (chunk == null) continue;

                // Perform random ticks for this chunk
                for (int i = 0; i < RANDOM_TICKS_PER_CHUNK; i++) {
                    int lx = random.nextInt(WorldConstants.CHUNK_SIZE);
                    int ly = random.nextInt(WorldConstants.WORLD_HEIGHT);
                    int lz = random.nextInt(WorldConstants.CHUNK_SIZE);

                    int blockId = chunk.getBlock(lx, ly, lz);

                    // Process wheat crops
                    if (Blocks.isWheatCrop(blockId)) {
                        int wx = cx * WorldConstants.CHUNK_SIZE + lx;
                        int wz = cz * WorldConstants.CHUNK_SIZE + lz;
                        randomTickWheat(world, wx, ly, wz, blockId, cx, cz);
                    }
                    // Farmland drying: if no water nearby and no crop on top, revert to dirt
                    else if (Blocks.isFarmland(blockId)) {
                        int wx = cx * WorldConstants.CHUNK_SIZE + lx;
                        int wz = cz * WorldConstants.CHUNK_SIZE + lz;
                        randomTickFarmland(world, wx, ly, wz, cx, cz);
                    }
                }
            }
        }

        return dirtyChunks;
    }

    /**
     * Process a random tick for a wheat crop block.
     */
    private void randomTickWheat(World world, int wx, int wy, int wz, int blockId, int cx, int cz) {
        int stage = Blocks.getWheatStage(blockId);
        if (stage >= 7) return; // Already mature

        // Check for hydration bonus (water within 4 blocks of the farmland below)
        float growthChance = BASE_GROWTH_CHANCE;
        boolean hydrated = isHydrated(world, wx, wy - 1, wz);
        if (hydrated) {
            growthChance *= HYDRATED_GROWTH_MULTIPLIER;
        }

        if (random.nextFloat() < growthChance) {
            // Advance to next stage
            int nextStage = stage + 1;
            world.setBlock(wx, wy, wz, Blocks.wheatCropId(nextStage));
            dirtyChunks.add(new ChunkPos(cx, cz));

            // Also dirty neighbor chunks if at chunk border
            int lx = wx - cx * WorldConstants.CHUNK_SIZE;
            int lz = wz - cz * WorldConstants.CHUNK_SIZE;
            if (lx == 0) dirtyChunks.add(new ChunkPos(cx - 1, cz));
            if (lx == WorldConstants.CHUNK_SIZE - 1) dirtyChunks.add(new ChunkPos(cx + 1, cz));
            if (lz == 0) dirtyChunks.add(new ChunkPos(cx, cz - 1));
            if (lz == WorldConstants.CHUNK_SIZE - 1) dirtyChunks.add(new ChunkPos(cx, cz + 1));
        }
    }

    /**
     * Process a random tick for a farmland block.
     * If there's no water nearby and no crop on top, slowly revert to dirt.
     */
    private void randomTickFarmland(World world, int wx, int wy, int wz, int cx, int cz) {
        // Check if there's a crop on top
        int aboveId = world.getBlock(wx, wy + 1, wz);
        if (Blocks.isWheatCrop(aboveId)) return; // Don't dry out if crop is planted

        // Check for water
        if (!isHydrated(world, wx, wy, wz)) {
            // Small chance to revert to dirt if not hydrated and no crop
            if (random.nextFloat() < 0.1f) {
                world.setBlock(wx, wy, wz, Blocks.DIRT.id());
                dirtyChunks.add(new ChunkPos(cx, cz));
            }
        }
    }

    /**
     * Check if a position has water within WATER_SEARCH_RADIUS blocks horizontally
     * and at the same Y level or one block above/below.
     */
    public static boolean isHydrated(World world, int wx, int wy, int wz) {
        for (int dx = -WATER_SEARCH_RADIUS; dx <= WATER_SEARCH_RADIUS; dx++) {
            for (int dz = -WATER_SEARCH_RADIUS; dz <= WATER_SEARCH_RADIUS; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int checkId = world.getBlock(wx + dx, wy + dy, wz + dz);
                    if (Blocks.isWater(checkId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
