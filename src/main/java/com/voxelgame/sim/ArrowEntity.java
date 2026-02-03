package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * Arrow projectile entity.
 * Flies in a straight line with gravity, sticks to blocks, damages entities.
 */
public class ArrowEntity extends Entity {
    private static final float HALF_WIDTH_VAL = 0.15f;
    private static final float HEIGHT_VAL = 0.15f;
    private static final float GRAVITY = -20.0f; // Same as player
    private static final float DRAG = 0.99f;
    
    private Vector3f velocity;
    private float damage;
    private boolean stuck = false;
    private int stuckX, stuckY, stuckZ;
    private float lifetime = 0;
    private static final float MAX_LIFETIME = 60.0f; // Despawn after 60s
    private Player shooter; // Who shot this arrow

    public ArrowEntity(float x, float y, float z, Vector3f direction, float speed, float damage, Player shooter) {
        super(EntityType.ARROW, x, y, z, HALF_WIDTH_VAL, HEIGHT_VAL, 1.0f);
        this.velocity = new Vector3f(direction).normalize().mul(speed);
        this.damage = damage;
        this.shooter = shooter;
        
        // Set yaw based on direction
        this.yaw = (float) Math.toDegrees(Math.atan2(direction.x, direction.z));
    }

    @Override
    public void update(float dt, World world, Player player) {
        if (dead) return;

        lifetime += dt;
        if (lifetime >= MAX_LIFETIME) {
            dead = true;
            return;
        }

        if (stuck) {
            // Check if stuck block still exists
            if (world.getBlock(stuckX, stuckY, stuckZ) == 0) {
                // Block broken, resume flight
                stuck = false;
            } else {
                // Allow player to pick up stuck arrow
                if (player != null) {
                    float dx = player.getPosition().x - x;
                    float dy = player.getPosition().y - y;
                    float dz = player.getPosition().z - z;
                    float distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq < 1.0f) { // Within 1 block
                        // Try to add arrow to inventory
                        int remaining = player.getInventory().addItem(Blocks.ARROW_ITEM.id(), 1);
                        if (remaining == 0) {
                            // Successfully picked up
                            dead = true;
                            System.out.println("[Arrow] Picked up by player");
                        }
                    }
                }
                return; // Stay stuck
            }
        }

        // Apply gravity
        velocity.y += GRAVITY * dt;

        // Apply drag
        velocity.mul(DRAG);

        // Move
        float newX = x + velocity.x * dt;
        float newY = y + velocity.y * dt;
        float newZ = z + velocity.z * dt;

        // Check block collision
        int blockX = (int) Math.floor(newX);
        int blockY = (int) Math.floor(newY);
        int blockZ = (int) Math.floor(newZ);

        if (world.getBlock(blockX, blockY, blockZ) != 0) {
            // Hit a block, stick to it
            stuck = true;
            stuckX = blockX;
            stuckY = blockY;
            stuckZ = blockZ;
            System.out.printf("[Arrow] Stuck in block at (%d, %d, %d)%n", blockX, blockY, blockZ);
            return;
        }

        // Check entity collision (but not the shooter)
        // TODO: Implement entity hit detection

        // Update position
        x = newX;
        y = newY;
        z = newZ;

        // Update rotation to face direction of flight
        if (velocity.lengthSquared() > 0.01f) {
            yaw = (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z));
        }
    }

    public boolean isStuck() {
        return stuck;
    }

    @Override
    public void onDeath(ItemEntityManager itemManager) {
        // Don't drop anything (already handled via pickup)
    }
}
