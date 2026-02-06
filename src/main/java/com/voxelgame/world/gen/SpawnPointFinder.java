package com.voxelgame.world.gen;

import com.voxelgame.world.WorldConstants;

/**
 * Finds a suitable player spawn point. Searches outward from (0,0)
 * for a location above sea level. Spawns at Y=120 (well above any terrain)
 * so the player is GUARANTEED to be in open sky and will fall to ground.
 */
public class SpawnPointFinder {

    // Fixed spawn height - well above max terrain to guarantee open sky
    // Player will fall to the ground after spawning
    private static final int SAFE_SPAWN_Y = 120;

    /**
     * Result of spawn point search.
     */
    public record SpawnPoint(double x, double y, double z) {}

    /**
     * Find a good spawn point using the generation context.
     * Searches in a spiral pattern from world origin (0,0) for land above sea level.
     * Spawns at fixed high Y to guarantee open sky - player falls to ground.
     *
     * @param context Generation context with terrain height function
     * @return spawn point (x, y, z) where y is high above ground (player will fall)
     */
    public static SpawnPoint find(GenContext context) {
        // Spiral search outward from origin for land above sea level
        int maxRadius = 200;

        for (int radius = 0; radius < maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Only check the ring, not the interior (optimization)
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    int wx = dx;
                    int wz = dz;

                    int height = context.getTerrainHeight(wx, wz);

                    // Must be above sea level (not in ocean)
                    if (height > WorldConstants.SEA_LEVEL + 2) {
                        // Spawn at fixed safe Y + eye level (1.62)
                        // Player will fall to the actual terrain
                        return new SpawnPoint(wx + 0.5, SAFE_SPAWN_Y + 1.62, wz + 0.5);
                    }
                }
            }
        }

        // Fallback: spawn high above origin
        return new SpawnPoint(0.5, SAFE_SPAWN_Y + 1.62, 0.5);
    }
}
