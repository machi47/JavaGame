package com.voxelgame.world.gen;

import com.voxelgame.world.WorldConstants;

/**
 * Finds a suitable player spawn point. Searches outward from (0,0)
 * for a location above sea level. Now properly spawns ABOVE the actual
 * terrain height, not at a fixed Y that could be inside mountains.
 */
public class SpawnPointFinder {

    // Spawn offset above terrain - enough to ensure clear air
    // 2 blocks above terrain = player feet at height+2, can fall 2 blocks without damage
    private static final int SPAWN_HEIGHT_OFFSET = 2;

    /**
     * Result of spawn point search.
     */
    public record SpawnPoint(double x, double y, double z) {}

    /**
     * Find a good spawn point using the generation context.
     * Searches in a spiral pattern from world origin (0,0) for land above sea level.
     * Spawns ABOVE the actual terrain height at that location.
     *
     * @param context Generation context with terrain height function
     * @return spawn point (x, y, z) where y is safely above ground
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
                        // Spawn safely above actual terrain height
                        // Add SPAWN_HEIGHT_OFFSET to clear any trees/structures
                        // Add eye level offset (1.62) for proper player positioning
                        int spawnY = Math.min(height + SPAWN_HEIGHT_OFFSET, 
                                              WorldConstants.WORLD_HEIGHT - 3);
                        return new SpawnPoint(wx + 0.5, spawnY + 1.62, wz + 0.5);
                    }
                }
            }
        }

        // Fallback: spawn above origin terrain
        int fallbackHeight = context.getTerrainHeight(0, 0);
        int fallbackY = Math.min(fallbackHeight + SPAWN_HEIGHT_OFFSET, 
                                 WorldConstants.WORLD_HEIGHT - 3);
        return new SpawnPoint(0.5, fallbackY + 1.62, 0.5);
    }
}
