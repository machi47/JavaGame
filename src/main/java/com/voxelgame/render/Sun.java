package com.voxelgame.render;

import com.voxelgame.world.WorldTime;
import org.joml.Vector3f;

/**
 * Manages sun direction and lighting properties derived from world time.
 * Provides directional lighting data for shaders.
 *
 * The sun orbits in the X-Y plane (rises in +X, sets in -X, Y is up).
 */
public class Sun {

    private final Vector3f direction = new Vector3f();
    private float intensity;

    /**
     * Update sun state from world time.
     * Call once per frame before rendering.
     */
    public void update(WorldTime worldTime) {
        if (worldTime == null) {
            // Default to noon if no world time
            direction.set(0, 1, 0);
            intensity = 1.0f;
            return;
        }

        // Sun angle: 0° = horizon east (sunrise), 90° = overhead (noon), 180° = horizon west (sunset)
        // WorldTime returns 0-360° over full day cycle
        float angleDeg = worldTime.getSunAngle();
        float angleRad = (float) Math.toRadians(angleDeg);

        // Sun direction in world space
        // At angle 0° (6 AM, sunrise): sun is at horizon east → direction = (1, 0, 0) pointing TO sun
        // At angle 90° (noon): sun is overhead → direction = (0, 1, 0)
        // At angle 180° (6 PM, sunset): sun is at horizon west → direction = (-1, 0, 0)
        // At angle 270° (midnight): sun is below → direction = (0, -1, 0)
        // We want direction FROM surface TO sun for N·L calculation
        // Use sin for Y (up at noon), cos for X (east-west)
        direction.set(
            (float) Math.cos(angleRad - Math.PI / 2),  // east → west
            (float) Math.sin(angleRad - Math.PI / 2),  // up at noon
            0.0f
        ).normalize();

        // Sun intensity based on height (Y component)
        // 1.0 when overhead, 0.0 when below horizon
        // Smoothly ramp up/down during sunrise/sunset
        float sunHeight = direction.y;
        if (sunHeight > 0) {
            // Sun is above horizon
            // Smooth ramp: weak at horizon, full when higher
            intensity = smoothstep(0.0f, 0.5f, sunHeight);
        } else {
            // Sun is below horizon (night) - no direct sunlight
            intensity = 0.0f;
        }
    }

    /**
     * Get normalized direction vector FROM surface TO sun.
     * Used for N·L lighting calculation.
     */
    public Vector3f getDirection() {
        return direction;
    }

    /**
     * Get sun intensity (0.0 = night, 1.0 = midday).
     */
    public float getIntensity() {
        return intensity;
    }

    /**
     * Smooth Hermite interpolation (smoothstep).
     */
    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }
}
