package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;

/**
 * Cow - Passive mob, wanders randomly, flees when attacked.
 * Drops: leather (0-2), raw beef (1-3).
 */
public class Cow extends Entity {
    private static final float HALF_WIDTH = 0.45f;
    private static final float HEIGHT_VAL = 1.4f;
    private static final float MAX_HP = 10.0f;
    private static final float WALK_SPEED = 1.0f;
    private static final float FLEE_SPEED = 3.5f;

    // Wander AI
    private float wanderTimer = 0;
    private boolean isWandering = false;
    private float wanderDirX = 0, wanderDirZ = 0;

    // Flee AI
    private boolean fleeing = false;
    private float fleeTimer = 0;
    private float fleeDirX = 0, fleeDirZ = 0;
    private float panicTimer = 0;

    public Cow(float x, float y, float z) {
        super(EntityType.COW, x, y, z, HALF_WIDTH, HEIGHT_VAL, MAX_HP);
        wanderTimer = 2.0f + random.nextFloat() * 3.0f;
    }

    @Override
    public void damage(float amount, float knockbackX, float knockbackZ) {
        super.damage(amount, knockbackX, knockbackZ);
        
        if (!dead) {
            // Start fleeing
            fleeing = true;
            fleeTimer = 3.0f + random.nextFloat() * 2.0f;
            panicTimer = 0;
            
            // Flee away from damage source
            float len = (float) Math.sqrt(knockbackX * knockbackX + knockbackZ * knockbackZ);
            if (len > 0.01f) {
                fleeDirX = knockbackX / len;
                fleeDirZ = knockbackZ / len;
            } else {
                // Random direction if no knockback vector
                float angle = random.nextFloat() * (float) (Math.PI * 2);
                fleeDirX = (float) Math.sin(angle);
                fleeDirZ = (float) Math.cos(angle);
            }
        }
    }

    @Override
    public void update(float dt, World world, Player player) {
        if (dead) return;
        
        age += dt;

        if (fleeing) {
            fleeTimer -= dt;
            if (fleeTimer <= 0) {
                // Stop fleeing
                fleeing = false;
                wanderTimer = 2.0f + random.nextFloat() * 3.0f;
                isWandering = false;
            } else {
                // Panic: jitter flee direction
                panicTimer -= dt;
                if (panicTimer <= 0) {
                    panicTimer = 0.3f + random.nextFloat() * 0.5f;
                    fleeDirX += (random.nextFloat() - 0.5f) * 0.8f;
                    fleeDirZ += (random.nextFloat() - 0.5f) * 0.8f;
                    float len = (float) Math.sqrt(fleeDirX * fleeDirX + fleeDirZ * fleeDirZ);
                    if (len > 0.01f) {
                        fleeDirX /= len;
                        fleeDirZ /= len;
                    }
                }
                
                vx = fleeDirX * FLEE_SPEED;
                vz = fleeDirZ * FLEE_SPEED;
                yaw = (float) Math.toDegrees(Math.atan2(fleeDirX, fleeDirZ));
                
                // Jump if stuck
                if (onGround && Math.abs(vx) < 0.05f && Math.abs(vz) < 0.05f) {
                    jump();
                    panicTimer = 0;
                }
            }
        } else {
            // Wander AI
            wanderTimer -= dt;
            if (wanderTimer <= 0) {
                if (isWandering) {
                    // Stop wandering
                    isWandering = false;
                    wanderTimer = 2.0f + random.nextFloat() * 3.0f;
                } else {
                    // Start wandering
                    isWandering = true;
                    wanderTimer = 1.0f + random.nextFloat() * 3.0f;
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
        // Drop leather (0-2)
        int leatherCount = random.nextInt(3); // 0, 1, or 2
        if (leatherCount > 0) {
            itemManager.spawnDrop(Blocks.LEATHER.id(), leatherCount, x, y, z);
        }
        
        // Drop raw beef (1-3)
        int beefCount = 1 + random.nextInt(3); // 1, 2, or 3
        itemManager.spawnDrop(Blocks.RAW_BEEF.id(), beefCount, x, y, z);
    }
}
