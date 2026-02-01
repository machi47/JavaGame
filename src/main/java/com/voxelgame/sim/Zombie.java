package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * Zombie — a hostile mob.
 *
 * AI: Targets and chases the player within 16 blocks. Attacks on contact.
 *     When player is out of range, wanders randomly.
 * Health: 20 HP.
 * Attack: 1 HP/sec on contact (scaled by game mode difficulty).
 * Drops: 0-2 rotten flesh on death.
 * Appearance: Green cube with red eyes.
 * Spawns: At night in dark areas.
 */
public class Zombie extends Entity {

    // ---- Dimensions (same proportions as player) ----
    private static final float HALF_WIDTH  = 0.3f;   // 0.6 wide
    private static final float HEIGHT_VAL  = 1.9f;   // slightly taller than player
    private static final float MAX_HP      = 20.0f;

    // ---- Movement ----
    private static final float WALK_SPEED  = 1.2f;   // blocks/sec (slower than player)
    private static final float CHASE_SPEED = 2.5f;   // blocks/sec when targeting player

    // ---- Targeting ----
    private static final float TARGET_RANGE    = 16.0f;
    private static final float TARGET_RANGE_SQ = TARGET_RANGE * TARGET_RANGE;

    // ---- Attack ----
    private static final float ATTACK_DAMAGE   = 1.0f;   // per second (base)
    private static final float ATTACK_INTERVAL = 1.0f;   // seconds between attacks
    private float attackTimer = 0;

    // ---- Wander state (used when player is out of range) ----
    private float wanderTimer = 0;
    private boolean isWandering = false;
    private float wanderDirX = 0, wanderDirZ = 0;

    // ---- Stuck detection ----
    private float stuckTimer = 0;
    private float lastX, lastZ;

    public Zombie(float x, float y, float z) {
        super(EntityType.ZOMBIE, x, y, z, HALF_WIDTH, HEIGHT_VAL, MAX_HP);
        lastX = x;
        lastZ = z;
    }

    @Override
    public void update(float dt, World world, Player player) {
        if (dead) return;
        age += dt;

        // ---- Check distance to player ----
        Vector3f pPos = player.getPosition();
        float dx = pPos.x - x;
        float dz = pPos.z - z;
        float distSq = dx * dx + dz * dz;

        boolean playerInRange = distSq < TARGET_RANGE_SQ && !player.isDead();

        if (playerInRange) {
            // ---- Chase player ----
            float dist = (float) Math.sqrt(distSq);
            if (dist > 0.5f) {
                float ndx = dx / dist;
                float ndz = dz / dist;

                vx = ndx * CHASE_SPEED;
                vz = ndz * CHASE_SPEED;

                // Face the player
                yaw = (float) Math.toDegrees(Math.atan2(ndx, ndz));
            }

            // ---- Attack on contact ----
            if (isCollidingWithPlayer(player)) {
                attackTimer += dt;
                if (attackTimer >= ATTACK_INTERVAL) {
                    player.damage(ATTACK_DAMAGE, DamageSource.MOB);
                    attackTimer = 0;
                }
            } else {
                attackTimer = 0;
            }

            // ---- Stuck detection: if barely moved in 1.5 seconds, try jumping ----
            stuckTimer += dt;
            if (stuckTimer > 1.5f) {
                float movedX = x - lastX;
                float movedZ = z - lastZ;
                if (movedX * movedX + movedZ * movedZ < 0.25f) {
                    jump(); // try to jump over obstacle
                }
                lastX = x;
                lastZ = z;
                stuckTimer = 0;
            }
        } else {
            // ---- Wander when player is out of range ----
            attackTimer = 0;
            stuckTimer = 0;
            wanderTimer -= dt;

            if (wanderTimer <= 0) {
                if (isWandering) {
                    isWandering = false;
                    wanderTimer = 3.0f + random.nextFloat() * 4.0f;
                } else {
                    isWandering = true;
                    wanderTimer = 1.0f + random.nextFloat() * 2.0f;
                    float angle = random.nextFloat() * (float) (Math.PI * 2);
                    wanderDirX = (float) Math.sin(angle);
                    wanderDirZ = (float) Math.cos(angle);
                }
            }

            if (isWandering) {
                vx = wanderDirX * WALK_SPEED;
                vz = wanderDirZ * WALK_SPEED;
                yaw = (float) Math.toDegrees(Math.atan2(wanderDirX, wanderDirZ));
            } else {
                vx = 0;
                vz = 0;
            }
        }

        moveWithCollision(dt, world);
    }

    @Override
    public void onDeath(ItemEntityManager itemManager) {
        int count = random.nextInt(3); // 0-2
        if (count > 0) {
            itemManager.spawnDrop(Blocks.ROTTEN_FLESH.id(), count, x, y, z);
        }
        System.out.printf("[Mob] Zombie died at (%.1f, %.1f, %.1f), dropped %d rotten flesh%n",
                x, y, z, count);
    }
}
