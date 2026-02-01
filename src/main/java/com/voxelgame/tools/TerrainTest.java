package com.voxelgame.tools;

import com.voxelgame.world.gen.*;
import com.voxelgame.world.*;

/**
 * Quick standalone test for terrain generation.
 * Run: ./gradlew run -PmainClass=com.voxelgame.tools.TerrainTest
 */
public class TerrainTest {
    public static void main(String[] args) {
        long seed = Long.parseLong(args.length > 0 ? args[0] : "12345");
        GenConfig config = GenConfig.defaultConfig();
        GenContext context = new GenContext(seed, config);
        Infdev611TerrainPass terrain = new Infdev611TerrainPass(seed);
        context.setInfdev611Terrain(terrain);

        // Test height at several points (wider area for ocean detection)
        System.out.println("=== Terrain Height Sample (wider area) ===");
        int belowSea = 0, aboveSea = 0, atSea = 0, total = 0;
        int minH = 999, maxH = 0;
        for (int x = -200; x <= 200; x += 5) {
            for (int z = -200; z <= 200; z += 5) {
                int h = terrain.getTerrainHeight(x, z);
                if (h < 64) belowSea++;
                else if (h == 64) atSea++;
                else aboveSea++;
                if (h < minH) minH = h;
                if (h > maxH) maxH = h;
                total++;
            }
        }
        System.out.printf("Total samples: %d%n", total);
        System.out.printf("Below sea level: %d (%.1f%%)%n", belowSea, 100.0 * belowSea / total);
        System.out.printf("At sea level:    %d (%.1f%%)%n", atSea, 100.0 * atSea / total);
        System.out.printf("Above sea level: %d (%.1f%%)%n", aboveSea, 100.0 * aboveSea / total);
        System.out.printf("Height range: %d to %d%n", minH, maxH);

        // Generate a chunk
        System.out.println("\n=== Generating chunk at (0,0) ===");
        Chunk chunk = new Chunk(new ChunkPos(0, 0));
        terrain.apply(chunk, context);

        // Count blocks
        int stone = 0, air = 0;
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int b = chunk.getBlock(x, y, z);
                    if (b == 1) stone++;
                    else if (b == 0) air++;
                }
            }
        }
        System.out.printf("Stone: %d, Air: %d (%.1f%% solid)%n", stone, air,
            100.0 * stone / (stone + air));

        // Print height profile
        System.out.println("\nHeight profile (x=8, z=0-15):");
        for (int z = 0; z < 16; z++) {
            int maxY = 0;
            for (int y = 127; y >= 0; y--) {
                if (chunk.getBlock(8, y, z) == 1) {
                    maxY = y;
                    break;
                }
            }
            System.out.printf("  z=%2d: y=%d%n", z, maxY);
        }

        // Test spawn point finding
        System.out.println("\n=== Spawn Point ===");
        SpawnPointFinder.SpawnPoint sp = SpawnPointFinder.find(context);
        System.out.printf("Spawn: (%.1f, %.1f, %.1f)%n", sp.x(), sp.y(), sp.z());
        System.out.printf("Spawn terrain height: %d%n", terrain.getTerrainHeight((int)sp.x(), (int)sp.z()));

        System.out.println("\n=== Terrain Test Complete ===");
    }
}
