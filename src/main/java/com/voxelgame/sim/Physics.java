package com.voxelgame.sim;

import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * Physics simulation. Applies gravity, terminal velocity clamping,
 * and delegates to Collision for world-aware movement resolution.
 * In fly mode, physics is bypassed (controller moves player directly).
 *
 * Also tracks fall distance and applies fall damage on landing.
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

    private World world;

    public void setWorld(World world) {
        this.world = world;
    }

    /**
     * Advance physics one step.
     * Applies gravity, then resolves collisions and integrates position.
     * Tracks fall distance and applies fall damage on landing.
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
            return;
        }

        Vector3f vel = player.getVelocity();

        // Record pre-step ground state for fall detection
        boolean wasOnGround = player.isOnGround();

        // --- Gravity ---
        vel.y -= GRAVITY * dt;
        if (vel.y < -TERMINAL_VELOCITY) {
            vel.y = -TERMINAL_VELOCITY;
        }

        // --- Collision resolution + position integration ---
        if (world != null) {
            Collision.resolveMovement(player.getPosition(), vel, dt, world, player);
        } else {
            // No world reference: raw integration (for testing)
            Vector3f pos = player.getPosition();
            pos.x += vel.x * dt;
            pos.y += vel.y * dt;
            pos.z += vel.z * dt;
        }

        // --- Fall tracking & damage ---
        feetY = player.getPosition().y - Player.EYE_HEIGHT;

        if (player.isOnGround()) {
            // Just landed or still on ground
            if (player.isTrackingFall()) {
                float fallDistance = player.landAndGetFallDistance(feetY);
                if (fallDistance > FALL_DAMAGE_THRESHOLD) {
                    float damage = (fallDistance - FALL_DAMAGE_THRESHOLD) * FALL_DAMAGE_PER_BLOCK;
                    player.damage(damage, DamageSource.FALL);
                }
            }
        } else {
            // Airborne — track highest point
            player.updateFallTracking(feetY);
        }
    }
}
