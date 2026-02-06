package com.voxelgame.world.gen;

import com.voxelgame.world.WorldConstants;

/**
 * Finds a suitable player spawn point. Searches outward from (0,0)
 * for a location above sea level. Spawns several blocks above the
 * terrain height to avoid spawning inside caves/overhangs.
 * Player will fall to the ground if in air - that's fine.
 */
public class SpawnPointFinder {

    // Extra height above terrain to avoid spawning inside mountains/caves
    private static final int SPAWN_HEIGHT_MARGIN = 5;

    /**
     * Result of spawn point search.
     */
    public record SpawnPoint(double x, double y, double z) {}

    /**
     * Find a good spawn point using the generation context.
     * Searches in a spiral pattern from world origin (0,0).
     * 
     * Requirements:
     * - Above sea level
     * - Terrain height + margin to avoid spawning in caves
     *
     * @param context Generation context with terrain height function
     * @return spawn point (x, y, z) where y is eye level above ground
     */
    public static SpawnPoint find(GenContext context) {
        // Spiral search outward from origin
        int maxRadius = 200;

        for (int radius = 0; radius < maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Only check the ring, not the interior (optimization)
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    int wx = dx;
                    int wz = dz;

                    int height = context.getTerrainHeight(wx, wz);

                    // Must be above sea level with some margin
                    // Spawn higher than terrain to avoid caves/overhangs
                    if (height > WorldConstants.SEA_LEVEL + 2 && height < 120) {
                        // Spawn at terrain height + margin + eye level (1.62)
                        // Player will fall to ground if spawned in air
                        int spawnY = height + SPAWN_HEIGHT_MARGIN;
                        return new SpawnPoint(wx + 0.5, spawnY + 1.62, wz + 0.5);
                    }
                }
            }
        }

        // Fallback: spawn high above origin
        int fallbackHeight = context.getTerrainHeight(0, 0);
        return new SpawnPoint(0.5, fallbackHeight + SPAWN_HEIGHT_MARGIN + 1.62, 0.5);
    }
}
