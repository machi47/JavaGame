package com.voxelgame.tools;

import com.voxelgame.world.gen.*;
import com.voxelgame.world.*;

/**
 * Quick standalone test for terrain generation.
 * Run: ./gradlew run -PmainClass=com.voxelgame.tools.TerrainTest
 */
public class TerrainTest {
    public static void main(String[] args) {
        long seed = 12345L;
        GenConfig config = GenConfig.defaultConfig();
        GenContext context = new GenContext(seed, config);
        Infdev611TerrainPass terrain = new Infdev611TerrainPass(seed);
        context.setInfdev611Terrain(terrain);

        // Test height at several points
        System.out.println("=== Terrain Height Sample ===");
        for (int x = -50; x <= 50; x += 10) {
            for (int z = -50; z <= 50; z += 10) {
                int h = terrain.getTerrainHeight(x, z);
                System.out.printf("(%4d, %4d) height=%d%n", x, z, h);
            }
        }

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

        System.out.println("\n=== Terrain Test Complete ===");
    }
}
