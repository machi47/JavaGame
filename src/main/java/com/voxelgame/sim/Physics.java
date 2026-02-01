package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;
import org.joml.Vector3f;

/**
 * Physics simulation. Applies gravity, terminal velocity clamping,
 * and delegates to Collision for world-aware movement resolution.
 * In fly mode, physics is bypassed (controller moves player directly).
 *
 * Also tracks fall distance and applies fall damage on landing.
 * Includes swimming mechanics: buoyancy, reduced speed, oxygen/drowning.
 */
public class Physics {

    public static final float GRAVITY          = 32.0f;   // blocks/s²
    public static final float TERMINAL_VELOCITY = 78.0f;  // blocks/s (max fall speed)
    public static final float JUMP_VELOCITY     = 9.0f;   // blocks/s upward (~1.27 block jump)

    /** Minimum fall distance (blocks) before damage is applied. */
    private static final float FALL_DAMAGE_THRESHOLD = 3.0f;
    /** Damage per block fallen beyond threshold. */
    private static final float FALL_DAMAGE_PER_BLOCK = 2.0f;
    /** Void death Y level (feet below this = instant death). */
    private static final float VOID_Y = -64.0f;

    // ---- Swimming constants ----
    /** Speed multiplier when in water. */
    public static final float WATER_SPEED_MULTIPLIER = 0.5f;
    /** Gravity when in water (reduced, acts as buoyancy-like). */
    private static final float WATER_GRAVITY = 6.0f;
    /** Upward buoyancy speed when pressing space in water. */
    private static final float WATER_SWIM_UP_SPEED = 4.0f;
    /** Terminal velocity in water (much lower). */
    private static final float WATER_TERMINAL_VELOCITY = 8.0f;
    /** Maximum oxygen in seconds. */
    public static final float MAX_OXYGEN = 10.0f;
    /** Drowning damage per second when out of oxygen. */
    private static final float DROWNING_DAMAGE_PER_SECOND = 2.0f;

    private World world;

    public void setWorld(World world) {
        this.world = world;
    }

    /**
     * Check if a position is inside a water block.
     */
    public boolean isInWater(float x, float y, float z) {
        if (world == null) return false;
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        if (by < 0 || by >= WorldConstants.WORLD_HEIGHT) return false;
        return world.getBlock(bx, by, bz) == Blocks.WATER.id();
    }

    /**
     * Advance physics one step.
     * Applies gravity, then resolves collisions and integrates position.
     * Tracks fall distance and applies fall damage on landing.
     * Handles swimming, buoyancy, and drowning.
     */
    public void step(Player player, float dt) {
        if (player.isDead()) return; // No physics when dead

        // Void check — instant kill if below void level
        float feetY = player.getPosition().y - Player.EYE_HEIGHT;
        if (feetY < VOID_Y) {
            player.damage(999.0f, DamageSource.VOID);
            return;
        }

        if (player.isFlyMode()) {
            player.resetFallTracking();
            player.setInWater(false);
            return;
        }

        Vector3f pos = player.getPosition();
        Vector3f vel = player.getVelocity();

        // ---- Water detection ----
        // Check if player's feet or body is in water
        boolean feetInWater = isInWater(pos.x, pos.y - Player.EYE_HEIGHT + 0.1f, pos.z);
        boolean headInWater = isInWater(pos.x, pos.y, pos.z);
        boolean bodyInWater = feetInWater || isInWater(pos.x, pos.y - Player.EYE_HEIGHT + 0.9f, pos.z);
        player.setInWater(bodyInWater);
        player.setHeadUnderwater(headInWater);

        // ---- Oxygen / Drowning ----
        if (headInWater) {
            // Drain oxygen
            player.drainOxygen(dt);
            if (player.getOxygen() <= 0) {
                // Drowning damage
                player.damage(DROWNING_DAMAGE_PER_SECOND * dt, DamageSource.DROWNING);
            }
        } else {
            // Refill oxygen instantly when head is above water
            player.refillOxygen();
        }

        // Record pre-step ground state for fall detection
        boolean wasOnGround = player.isOnGround();

        if (bodyInWater) {
            // ---- Water physics ----
            // Reduced gravity (buoyancy)
            vel.y -= WATER_GRAVITY * dt;

            // Clamp sink speed
            if (vel.y < -WATER_TERMINAL_VELOCITY) {
                vel.y = -WATER_TERMINAL_VELOCITY;
            }

            // Water friction on horizontal movement
            vel.x *= (1.0f - 3.0f * dt);
            vel.z *= (1.0f - 3.0f * dt);

            // Reset fall tracking when in water (water breaks fall)
            if (player.isTrackingFall()) {
                player.landAndGetFallDistance(feetY); // Reset without damage
            }
        } else {
            // ---- Normal physics ----
            vel.y -= GRAVITY * dt;
            if (vel.y < -TERMINAL_VELOCITY) {
                vel.y = -TERMINAL_VELOCITY;
            }
        }

        // --- Collision resolution + position integration ---
        if (world != null) {
            Collision.resolveMovement(player.getPosition(), vel, dt, world, player);
        } else {
            // No world reference: raw integration (for testing)
            pos.x += vel.x * dt;
            pos.y += vel.y * dt;
            pos.z += vel.z * dt;
        }

        // --- Fall tracking & damage ---
        feetY = player.getPosition().y - Player.EYE_HEIGHT;

        if (!bodyInWater) {
            if (player.isOnGround()) {
                if (player.isTrackingFall()) {
                    float fallDistance = player.landAndGetFallDistance(feetY);
                    if (fallDistance > FALL_DAMAGE_THRESHOLD) {
                        float damage = (fallDistance - FALL_DAMAGE_THRESHOLD) * FALL_DAMAGE_PER_BLOCK;
                        player.damage(damage, DamageSource.FALL);
                    }
                }
            } else {
                player.updateFallTracking(feetY);
            }
        }
    }
}
