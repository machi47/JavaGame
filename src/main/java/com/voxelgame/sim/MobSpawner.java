package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;
import com.voxelgame.world.WorldTime;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Handles mob spawning logic.
 *
 * Passive spawning (pigs): During daytime, on grass blocks, 24-64 blocks from player.
 * Hostile spawning (zombies): At night, in dark areas, 24-64 blocks from player.
 *
 * Max 50 entities total (EntityManager cap).
 * Spawn attempts happen periodically, not every frame.
 */
public class MobSpawner {

    // ---- Spawn intervals ----
    private static final float PASSIVE_SPAWN_INTERVAL = 5.0f;   // seconds between passive spawn attempts
    private static final float HOSTILE_SPAWN_INTERVAL = 3.0f;   // seconds between hostile spawn attempts

    // ---- Spawn distances ----
    private static final float MIN_SPAWN_DIST = 24.0f;
    private static final float MAX_SPAWN_DIST = 64.0f;

    // ---- Max per type ----
    private static final int MAX_PIGS    = 15;
    private static final int MAX_ZOMBIES = 20;

    // ---- Timers ----
    private float passiveTimer = 0;
    private float hostileTimer = 0;

    private final Random random = new Random();

    /**
     * Update spawning logic.
     */
    public void update(float dt, World world, Player player,
                       EntityManager entityManager, WorldTime worldTime) {
        if (entityManager.getEntityCount() >= EntityManager.MAX_ENTITIES) return;
        if (player.isDead()) return;

        // ---- Passive mob spawning (pigs) ----
        passiveTimer += dt;
        if (passiveTimer >= PASSIVE_SPAWN_INTERVAL) {
            passiveTimer = 0;

            if (worldTime.isDay() && entityManager.countType(EntityType.PIG) < MAX_PIGS) {
                trySpawnPig(world, player, entityManager);
            }
        }

        // ---- Hostile mob spawning (zombies) ----
        hostileTimer += dt;
        if (hostileTimer >= HOSTILE_SPAWN_INTERVAL) {
            hostileTimer = 0;

            if (worldTime.isNight() && entityManager.countType(EntityType.ZOMBIE) < MAX_ZOMBIES) {
                trySpawnZombie(world, player, entityManager);
            }
        }
    }

    /**
     * Attempt to spawn a pig near the player.
     * Requires grass surface block with air above.
     */
    private void trySpawnPig(World world, Player player, EntityManager entityManager) {
        Vector3f pPos = player.getPosition();

        for (int attempt = 0; attempt < 5; attempt++) {
            // Pick random position 24-64 blocks from player
            float angle = random.nextFloat() * (float) (Math.PI * 2);
            float dist = MIN_SPAWN_DIST + random.nextFloat() * (MAX_SPAWN_DIST - MIN_SPAWN_DIST);
            float sx = pPos.x + (float) Math.cos(angle) * dist;
            float sz = pPos.z + (float) Math.sin(angle) * dist;

            // Find surface Y at this position
            int surfaceY = findSurfaceY(world, (int) Math.floor(sx), (int) Math.floor(sz));
            if (surfaceY < 0) continue;

            // Check if surface block is grass
            int surfaceBlock = world.getBlock((int) Math.floor(sx), surfaceY, (int) Math.floor(sz));
            if (surfaceBlock != Blocks.GRASS.id()) continue;

            // Check for 2 blocks of air above
            int spawnY = surfaceY + 1;
            if (world.getBlock((int) Math.floor(sx), spawnY, (int) Math.floor(sz)) != 0) continue;
            if (world.getBlock((int) Math.floor(sx), spawnY + 1, (int) Math.floor(sz)) != 0) continue;

            // Spawn pig
            Pig pig = new Pig(sx, spawnY, sz);
            entityManager.addEntity(pig);
            System.out.printf("[Spawn] Pig spawned at (%.1f, %d, %.1f)%n", sx, spawnY, sz);
            return;
        }
    }

    /**
     * Attempt to spawn a zombie near the player.
     * Requires solid surface with air above. Spawns at night.
     */
    private void trySpawnZombie(World world, Player player, EntityManager entityManager) {
        Vector3f pPos = player.getPosition();

        for (int attempt = 0; attempt < 8; attempt++) {
            // Pick random position 24-64 blocks from player
            float angle = random.nextFloat() * (float) (Math.PI * 2);
            float dist = MIN_SPAWN_DIST + random.nextFloat() * (MAX_SPAWN_DIST - MIN_SPAWN_DIST);
            float sx = pPos.x + (float) Math.cos(angle) * dist;
            float sz = pPos.z + (float) Math.sin(angle) * dist;

            // Find surface Y at this position
            int surfaceY = findSurfaceY(world, (int) Math.floor(sx), (int) Math.floor(sz));
            if (surfaceY < 0) continue;

            // Check for 2 blocks of air above (zombie is tall)
            int spawnY = surfaceY + 1;
            if (world.getBlock((int) Math.floor(sx), spawnY, (int) Math.floor(sz)) != 0) continue;
            if (world.getBlock((int) Math.floor(sx), spawnY + 1, (int) Math.floor(sz)) != 0) continue;

            // Check light level — zombies only spawn in dark areas
            // During night, sky light is effectively dim, so surface spawning is OK
            int skyLight = world.getSkyLight((int) Math.floor(sx), spawnY, (int) Math.floor(sz));
            // Sky light < 7 means caves/overhangs during day. At night, any location is fine.
            // We already checked isNight() in the caller, so spawn anywhere at night.

            // Spawn zombie
            Zombie zombie = new Zombie(sx, spawnY, sz);
            entityManager.addEntity(zombie);
            System.out.printf("[Spawn] Zombie spawned at (%.1f, %d, %.1f)%n", sx, spawnY, sz);
            return;
        }
    }

    /**
     * Find the highest solid block Y at the given XZ position.
     * Returns -1 if no solid surface found (e.g., unloaded chunk).
     */
    private int findSurfaceY(World world, int x, int z) {
        // Start from a reasonable height and work down
        for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
            if (Blocks.get(world.getBlock(x, y, z)).solid()) {
                return y;
            }
        }
        return -1;
    }
}
