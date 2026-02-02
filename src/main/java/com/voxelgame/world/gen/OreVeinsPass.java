package com.voxelgame.world.gen;

import com.voxelgame.math.RNG;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Ore placement pass matching Minecraft's Infdev 611 algorithm.
 * Uses the classic Minecraft ore vein generation:
 * - Generate a start and end point
 * - Interpolate along a line between them
 * - At each point, place a sphere of ore blocks
 * - Sphere size varies along the line (thicker in middle, thinner at ends)
 */
public class OreVeinsPass implements GenPipeline.GenerationPass {

    @Override
    public void apply(Chunk chunk, GenContext context) {
        GenConfig config = context.getConfig();
        if (!config.oresEnabled) return;  // skip if ores disabled

        RNG rng = context.chunkRNG(chunk.getPos().x(), chunk.getPos().z());

        // Generate each ore type (applying abundance multiplier)
        generateOre(chunk, rng, Blocks.COAL_ORE.id(),
                    config.coalMinY, config.coalMaxY, config.coalVeinSize,
                    config.effectiveOreAttempts(config.coalAttemptsPerChunk));
        generateOre(chunk, rng, Blocks.IRON_ORE.id(),
                    config.ironMinY, config.ironMaxY, config.ironVeinSize,
                    config.effectiveOreAttempts(config.ironAttemptsPerChunk));
        generateOre(chunk, rng, Blocks.GOLD_ORE.id(),
                    config.goldMinY, config.goldMaxY, config.goldVeinSize,
                    config.effectiveOreAttempts(config.goldAttemptsPerChunk));
        generateOre(chunk, rng, Blocks.DIAMOND_ORE.id(),
                    config.diamondMinY, config.diamondMaxY, config.diamondVeinSize,
                    config.effectiveOreAttempts(config.diamondAttemptsPerChunk));
        generateOre(chunk, rng, Blocks.REDSTONE_ORE.id(),
                    config.redstoneMinY, config.redstoneMaxY, config.redstoneVeinSize,
                    config.effectiveOreAttempts(config.redstoneAttemptsPerChunk));
    }

    private void generateOre(Chunk chunk, RNG rng, int oreId,
                             int minY, int maxY, int veinSize, int attempts) {
        for (int a = 0; a < attempts; a++) {
            // Random origin within chunk
            int ox = rng.nextInt(WorldConstants.CHUNK_SIZE);
            int oy = minY + rng.nextInt(Math.max(1, maxY - minY));
            int oz = rng.nextInt(WorldConstants.CHUNK_SIZE);

            // Place vein using Minecraft's classic blob shape
            placeVein(chunk, rng, oreId, ox, oy, oz, veinSize);
        }
    }

    /**
     * Place a vein of ore blocks using Minecraft's classic blob algorithm.
     * Creates a line segment with spherical ore placement along it.
     * This matches the original ore generation from Infdev through Beta.
     */
    private void placeVein(Chunk chunk, RNG rng, int oreId,
                           int startX, int startY, int startZ, int size) {
        // Generate random angles for the vein direction
        double angle = rng.nextDouble() * Math.PI;
        
        // Start and end points offset from center
        double x1 = startX + Math.sin(angle) * size / 8.0;
        double x2 = startX - Math.sin(angle) * size / 8.0;
        double z1 = startZ + Math.cos(angle) * size / 8.0;
        double z2 = startZ - Math.cos(angle) * size / 8.0;
        double y1 = startY + rng.nextInt(3) - 2;
        double y2 = startY + rng.nextInt(3) - 2;
        
        // Interpolate along the line, placing spheres of ore
        for (int i = 0; i < size; i++) {
            double t = (double) i / (double) size;
            
            // Interpolate position
            double px = x1 + (x2 - x1) * t;
            double py = y1 + (y2 - y1) * t;
            double pz = z1 + (z2 - z1) * t;
            
            // Radius varies: thicker in middle, thinner at ends
            double radius = (Math.sin(t * Math.PI) * (rng.nextDouble() * size / 16.0 + 1.0)) + 0.5;
            
            // Place blocks in a sphere around this point
            int minBX = (int) Math.floor(px - radius);
            int maxBX = (int) Math.floor(px + radius);
            int minBY = (int) Math.floor(py - radius);
            int maxBY = (int) Math.floor(py + radius);
            int minBZ = (int) Math.floor(pz - radius);
            int maxBZ = (int) Math.floor(pz + radius);
            
            for (int bx = minBX; bx <= maxBX; bx++) {
                double dx = (bx + 0.5 - px) / radius;
                if (dx * dx >= 1.0) continue;
                
                for (int by = minBY; by <= maxBY; by++) {
                    double dy = (by + 0.5 - py) / radius;
                    if (dx * dx + dy * dy >= 1.0) continue;
                    
                    for (int bz = minBZ; bz <= maxBZ; bz++) {
                        double dz = (bz + 0.5 - pz) / radius;
                        if (dx * dx + dy * dy + dz * dz >= 1.0) continue;
                        
                        // Only place within chunk bounds and in stone
                        if (bx >= 0 && bx < WorldConstants.CHUNK_SIZE &&
                            by >= 1 && by < WorldConstants.WORLD_HEIGHT &&
                            bz >= 0 && bz < WorldConstants.CHUNK_SIZE) {
                            
                            if (chunk.getBlock(bx, by, bz) == Blocks.STONE.id()) {
                                chunk.setBlock(bx, by, bz, oreId);
                            }
                        }
                    }
                }
            }
        }
    }
}
