package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;

/**
 * Pig — a passive (friendly) mob.
 *
 * AI: Wanders randomly, avoids edges, faces movement direction.
 * Health: 10 HP.
 * Drops: 1-3 raw porkchops on death.
 * Appearance: Pink cube with dark eyes.
 * Spawns: On grass during daytime.
 */
public class Pig extends Entity {

    // ---- Dimensions ----
    private static final float HALF_WIDTH  = 0.4f;   // 0.8 wide
    private static final float HEIGHT      = 0.7f;   // short and squat
    private static final float MAX_HP      = 10.0f;

    // ---- Movement ----
    private static final float WALK_SPEED  = 1.5f;   // blocks/sec

    // ---- Wander AI state ----
    private float wanderTimer = 0;
    private boolean isWandering = false;
    private float wanderDirX = 0, wanderDirZ = 0;

    public Pig(float x, float y, float z) {
        super(EntityType.PIG, x, y, z, HALF_WIDTH, HEIGHT, MAX_HP);
        scheduleIdle();
    }

    @Override
    public void update(float dt, World world, Player player) {
        if (dead) return;
        age += dt;

        // ---- Wander AI ----
        wanderTimer -= dt;

        if (wanderTimer <= 0) {
            if (isWandering) {
                // Stop: idle for 2-5 seconds
                scheduleIdle();
            } else {
                // Start wandering in a random direction
                startWander();
            }
        }

        if (isWandering) {
            // Edge detection: check if there's ground ahead
            float aheadX = x + wanderDirX * 1.5f;
            float aheadZ = z + wanderDirZ * 1.5f;
            int groundY = (int) Math.floor(y) - 1;

            if (groundY >= 0 && !isSolid(world, (int) Math.floor(aheadX), groundY, (int) Math.floor(aheadZ))) {
                // No ground ahead — turn around
                wanderDirX = -wanderDirX;
                wanderDirZ = -wanderDirZ;
                yaw = (float) Math.toDegrees(Math.atan2(wanderDirX, wanderDirZ));
            }

            vx = wanderDirX * WALK_SPEED;
            vz = wanderDirZ * WALK_SPEED;

            // Update facing
            yaw = (float) Math.toDegrees(Math.atan2(wanderDirX, wanderDirZ));

            // If stuck (hit a wall), change direction
            if (onGround && Math.abs(vx) < 0.05f && Math.abs(vz) < 0.05f) {
                startWander(); // pick new direction
            }
        } else {
            vx = 0;
            vz = 0;
        }

        moveWithCollision(dt, world);
    }

    @Override
    public void onDeath(ItemEntityManager itemManager) {
        int count = 1 + random.nextInt(3); // 1-3
        itemManager.spawnDrop(Blocks.RAW_PORKCHOP.id(), count, x, y, z);
        System.out.printf("[Mob] Pig died at (%.1f, %.1f, %.1f), dropped %d raw porkchop(s)%n",
                x, y, z, count);
    }

    private void startWander() {
        isWandering = true;
        wanderTimer = 1.0f + random.nextFloat() * 3.0f;
        float angle = random.nextFloat() * (float) (Math.PI * 2);
        wanderDirX = (float) Math.sin(angle);
        wanderDirZ = (float) Math.cos(angle);
    }

    private void scheduleIdle() {
        isWandering = false;
        wanderTimer = 2.0f + random.nextFloat() * 3.0f;
    }
}
