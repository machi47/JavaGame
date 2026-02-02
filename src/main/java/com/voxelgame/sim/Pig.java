package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;

/**
 * Pig — a passive (friendly) mob.  (Infdev 611 parity)
 *
 * AI: Wanders randomly, avoids edges, faces movement direction.
 *     Flees when hit (panicked run for 3-5 seconds).
 * Health: 10 HP.
 * Drops: 0-2 raw porkchops on death (cooked if burning).
 * Appearance: Pink body with darker snout, short legs.
 * Spawns: On grass during daytime (light >= 7).
 *
 * Model proportions (Infdev 611 style):
 *   Body: 0.5 wide × 0.5 tall × 0.875 long
 *   Head: 0.5 × 0.5 × 0.5 with protruding snout
 *   Legs: 0.25 × 0.375 × 0.25 (short and stubby)
 */
public class Pig extends Entity {

    // ---- Dimensions (hitbox) ----
    private static final float HALF_WIDTH  = 0.45f;  // 0.9 wide hitbox
    private static final float HEIGHT      = 0.875f;  // legs(0.375) + body(0.5)
    private static final float MAX_HP      = 10.0f;

    // ---- Movement ----
    private static final float WALK_SPEED  = 1.5f;   // blocks/sec
    private static final float FLEE_SPEED  = 4.0f;   // blocks/sec (panicked)

    // ---- Wander AI state ----
    private float wanderTimer = 0;
    private boolean isWandering = false;
    private float wanderDirX = 0, wanderDirZ = 0;

    // ---- Flee state (when hit) ----
    private boolean fleeing = false;
    private float fleeTimer = 0;
    private float fleeDirX = 0, fleeDirZ = 0;
    private float panicTimer = 0;

    public Pig(float x, float y, float z) {
        super(EntityType.PIG, x, y, z, HALF_WIDTH, HEIGHT, MAX_HP);
        scheduleIdle();
    }

    @Override
    public void damage(float amount, float knockbackX, float knockbackZ) {
        super.damage(amount, knockbackX, knockbackZ);
        if (!dead) {
            startFlee(knockbackX, knockbackZ);
        }
    }

    @Override
    public void update(float dt, World world, Player player) {
        if (dead) return;
        age += dt;

        if (fleeing) {
            // ---- Flee AI (panicked) ----
            fleeTimer -= dt;
            if (fleeTimer <= 0) {
                fleeing = false;
                scheduleIdle();
            } else {
                // Periodically change direction slightly (panic behavior)
                panicTimer -= dt;
                if (panicTimer <= 0) {
                    panicTimer = 0.3f + random.nextFloat() * 0.5f;
                    fleeDirX += (random.nextFloat() - 0.5f) * 0.8f;
                    fleeDirZ += (random.nextFloat() - 0.5f) * 0.8f;
                    float len = (float) Math.sqrt(fleeDirX * fleeDirX + fleeDirZ * fleeDirZ);
                    if (len > 0.01f) { fleeDirX /= len; fleeDirZ /= len; }
                }

                // Edge detection during flee
                float aheadX = x + fleeDirX * 1.5f;
                float aheadZ = z + fleeDirZ * 1.5f;
                int groundY = (int) Math.floor(y) - 1;
                if (groundY >= 0 && !isSolid(world, (int) Math.floor(aheadX), groundY, (int) Math.floor(aheadZ))) {
                    fleeDirX = -fleeDirX;
                    fleeDirZ = -fleeDirZ;
                }

                vx = fleeDirX * FLEE_SPEED;
                vz = fleeDirZ * FLEE_SPEED;
                yaw = (float) Math.toDegrees(Math.atan2(fleeDirX, fleeDirZ));

                // Jump if stuck
                if (onGround && Math.abs(vx) < 0.05f && Math.abs(vz) < 0.05f) {
                    jump();
                    panicTimer = 0; // pick new direction
                }
            }
        } else {
            // ---- Normal wander AI ----
            wanderTimer -= dt;

            if (wanderTimer <= 0) {
                if (isWandering) {
                    scheduleIdle();
                } else {
                    startWander();
                }
            }

            if (isWandering) {
                // Edge detection: check if there's ground ahead
                float aheadX = x + wanderDirX * 1.5f;
                float aheadZ = z + wanderDirZ * 1.5f;
                int groundY = (int) Math.floor(y) - 1;

                if (groundY >= 0 && !isSolid(world, (int) Math.floor(aheadX), groundY, (int) Math.floor(aheadZ))) {
                    wanderDirX = -wanderDirX;
                    wanderDirZ = -wanderDirZ;
                    yaw = (float) Math.toDegrees(Math.atan2(wanderDirX, wanderDirZ));
                }

                vx = wanderDirX * WALK_SPEED;
                vz = wanderDirZ * WALK_SPEED;
                yaw = (float) Math.toDegrees(Math.atan2(wanderDirX, wanderDirZ));

                // If stuck (hit a wall), try jumping then change direction
                if (onGround && Math.abs(vx) < 0.05f && Math.abs(vz) < 0.05f) {
                    startWander();
                }
            } else {
                vx = 0;
                vz = 0;
            }
        }

        moveWithCollision(dt, world);
    }

    @Override
    public void onDeath(ItemEntityManager itemManager) {
        int count = random.nextInt(3); // 0-2 (Infdev 611: 0-2 raw porkchops)
        if (count > 0) {
            // Drop cooked porkchop if burning, raw porkchop otherwise
            int dropId = burning ? Blocks.COOKED_PORKCHOP.id() : Blocks.RAW_PORKCHOP.id();
            itemManager.spawnDrop(dropId, count, x, y, z);
            String dropName = burning ? "cooked porkchop" : "raw porkchop";
            System.out.printf("[Mob] Pig died at (%.1f, %.1f, %.1f), dropped %d %s(s)%n",
                    x, y, z, count, dropName);
        } else {
            System.out.printf("[Mob] Pig died at (%.1f, %.1f, %.1f), dropped nothing%n", x, y, z);
        }
    }

    // ---- AI helpers ----

    private void startFlee(float knockbackX, float knockbackZ) {
        fleeing = true;
        fleeTimer = 3.0f + random.nextFloat() * 2.0f; // 3-5 seconds
        panicTimer = 0;

        // Run in the knockback direction (away from attacker)
        float len = (float) Math.sqrt(knockbackX * knockbackX + knockbackZ * knockbackZ);
        if (len > 0.01f) {
            fleeDirX = knockbackX / len;
            fleeDirZ = knockbackZ / len;
        } else {
            float angle = random.nextFloat() * (float) (Math.PI * 2);
            fleeDirX = (float) Math.sin(angle);
            fleeDirZ = (float) Math.cos(angle);
        }
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
