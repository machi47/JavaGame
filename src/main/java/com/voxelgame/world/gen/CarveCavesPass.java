package com.voxelgame.world.gen;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Cave carving pass. Uses dual 3D Perlin noise (cheese caves) to carve
 * tunnel systems through terrain. Cave density increases with depth.
 * Avoids breaking through the surface unless on a hillside.
 */
public class CarveCavesPass implements GenPipeline.GenerationPass {

    @Override
    public void apply(Chunk chunk, GenContext context) {
        GenConfig config = context.getConfig();
        int chunkWorldX = chunk.getPos().worldX();
        int chunkWorldZ = chunk.getPos().worldZ();

        for (int lx = 0; lx < WorldConstants.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < WorldConstants.CHUNK_SIZE; lz++) {
                int worldX = chunkWorldX + lx;
                int worldZ = chunkWorldZ + lz;

                int surfaceHeight = context.getTerrainHeight(worldX, worldZ);

                // Carve from above bedrock up to below surface
                int maxCaveY = Math.min(surfaceHeight - config.caveSurfaceMargin,
                                        WorldConstants.WORLD_HEIGHT - 1);

                for (int y = config.caveMinY; y <= maxCaveY; y++) {
                    // Only carve solid blocks
                    int currentBlock = chunk.getBlock(lx, y, lz);
                    if (currentBlock == Blocks.AIR.id() || currentBlock == Blocks.BEDROCK.id()) {
                        continue;
                    }

                    double freq = config.caveFreq;

                    // Sample two noise fields — cave exists where both are near zero (spaghetti caves)
                    double n1 = context.getCaveNoise1().eval3D(
                        worldX * freq, y * freq, worldZ * freq);
                    double n2 = context.getCaveNoise2().eval3D(
                        worldX * freq + 500, y * freq + 500, worldZ * freq + 500);

                    // Spaghetti caves: carve where BOTH noise values are near zero
                    double combined = n1 * n1 + n2 * n2;

                    // Second tunnel system at different frequency for more interconnections
                    double freq2 = freq * 0.7;
                    double t1 = context.getCaveNoise2().eval3D(
                        worldX * freq2 + 2000, y * freq2 + 2000, worldZ * freq2 + 2000);
                    double t2 = context.getCaveNoise1().eval3D(
                        worldX * freq2 + 3000, y * freq2 + 3000, worldZ * freq2 + 3000);
                    double combined2 = t1 * t1 + t2 * t2;

                    // Also sample at a lower frequency for larger cave rooms
                    double roomFreq = freq * 0.4;
                    double roomN = context.getCaveNoise1().eval3D(
                        worldX * roomFreq + 1000, y * roomFreq + 1000, worldZ * roomFreq + 1000);
                    boolean isRoom = Math.abs(roomN) < 0.07;

                    // Depth factor: caves are more common deeper down
                    double depthFactor = 1.0 - ((double) y / surfaceHeight);
                    depthFactor = 0.6 + depthFactor * 0.4; // range [0.6, 1.0]

                    // Cave threshold — lower combined value = more likely to be cave
                    double threshold = config.caveThreshold * depthFactor;
                    double thresholdSq = threshold * threshold * 0.3;

                    if (combined < thresholdSq || combined2 < thresholdSq * 0.8 || isRoom) {
                        // Don't carve if block directly above is air/grass (surface protection)
                        // But allow it if we're deep enough below surface
                        if (y >= surfaceHeight - config.caveSurfaceMargin) {
                            continue;
                        }
                        chunk.setBlock(lx, y, lz, Blocks.AIR.id());
                    }
                }
            }
        }
    }
}
