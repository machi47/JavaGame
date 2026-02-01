package com.voxelgame.world.gen;

import com.voxelgame.math.RNG;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Tree placement pass. Uses InfDev 611-style patch-based placement:
 * - Forest patches determined by noise (not even distribution)
 * - Multiple tree clusters per chunk in forested areas
 * - Each patch places 5-12 trees in a cluster
 * - Trees only on grass, away from water and steep slopes
 */
public class TreesPass implements GenPipeline.GenerationPass {

    @Override
    public void apply(Chunk chunk, GenContext context) {
        GenConfig config = context.getConfig();
        RNG rng = context.chunkRNG(chunk.getPos().x() * 7, chunk.getPos().z() * 13);
        int chunkWorldX = chunk.getPos().worldX();
        int chunkWorldZ = chunk.getPos().worldZ();

        // Determine forest density for this chunk using large-scale noise
        int chunkCenterX = chunkWorldX + WorldConstants.CHUNK_SIZE / 2;
        int chunkCenterZ = chunkWorldZ + WorldConstants.CHUNK_SIZE / 2;

        double forestDensity = context.getForestNoise().eval2D(
            chunkCenterX * 0.005, chunkCenterZ * 0.005);

        // Dense forest: forestDensity > threshold → more patches
        // Sparse/no trees: forestDensity < -threshold
        if (forestDensity < -0.3) {
            // Barren area: very sparse, maybe 1 lone tree
            if (rng.nextDouble() < 0.15) {
                placeSingleTree(chunk, context, rng, config, chunkWorldX, chunkWorldZ);
            }
            return;
        }

        // Number of tree patches scales with forest density
        int numPatches;
        if (forestDensity > config.forestNoiseThreshold + 0.3) {
            // Dense forest: 2-4 patches
            numPatches = 2 + rng.nextInt(3);
        } else if (forestDensity > config.forestNoiseThreshold) {
            // Moderate forest: 1-2 patches
            numPatches = 1 + rng.nextInt(2);
        } else {
            // Light scattering: 0-1 patches
            numPatches = rng.nextDouble() < config.treePatchChance ? 1 : 0;
        }

        for (int patch = 0; patch < numPatches; patch++) {
            placePatch(chunk, context, rng, config, chunkWorldX, chunkWorldZ);
        }
    }

    /**
     * Place a patch of trees (cluster) within the chunk.
     * InfDev style: pick a center, then attempt to place several trees nearby.
     */
    private void placePatch(Chunk chunk, GenContext context, RNG rng,
                            GenConfig config, int chunkWorldX, int chunkWorldZ) {
        int margin = config.treeEdgeMargin;
        int usableSize = WorldConstants.CHUNK_SIZE - margin * 2;
        if (usableSize <= 0) return;

        // Patch center (local coordinates)
        int centerLX = margin + rng.nextInt(usableSize);
        int centerLZ = margin + rng.nextInt(usableSize);

        int treesPlaced = 0;
        int maxTrees = 5 + rng.nextInt(config.treePatchAttempts - 4);

        for (int attempt = 0; attempt < config.treePatchAttempts * 2 && treesPlaced < maxTrees; attempt++) {
            // Random offset from patch center
            int lx = centerLX + rng.nextInt(config.treePatchSpread * 2 + 1) - config.treePatchSpread;
            int lz = centerLZ + rng.nextInt(config.treePatchSpread * 2 + 1) - config.treePatchSpread;

            // Clamp to safe zone
            lx = Math.max(margin, Math.min(lx, WorldConstants.CHUNK_SIZE - margin - 1));
            lz = Math.max(margin, Math.min(lz, WorldConstants.CHUNK_SIZE - margin - 1));

            if (tryPlaceTree(chunk, context, rng, config, chunkWorldX, chunkWorldZ, lx, lz)) {
                treesPlaced++;
            }
        }
    }

    /**
     * Place a single lone tree somewhere in the chunk.
     */
    private void placeSingleTree(Chunk chunk, GenContext context, RNG rng,
                                  GenConfig config, int chunkWorldX, int chunkWorldZ) {
        int margin = config.treeEdgeMargin;
        int usableSize = WorldConstants.CHUNK_SIZE - margin * 2;
        if (usableSize <= 0) return;

        for (int attempt = 0; attempt < 5; attempt++) {
            int lx = margin + rng.nextInt(usableSize);
            int lz = margin + rng.nextInt(usableSize);
            if (tryPlaceTree(chunk, context, rng, config, chunkWorldX, chunkWorldZ, lx, lz)) {
                return;
            }
        }
    }

    /**
     * Try to place a tree at the given local position. Returns true if successful.
     */
    private boolean tryPlaceTree(Chunk chunk, GenContext context, RNG rng,
                                  GenConfig config, int chunkWorldX, int chunkWorldZ,
                                  int lx, int lz) {
        int worldX = chunkWorldX + lx;
        int worldZ = chunkWorldZ + lz;

        // Find the surface height
        int height = context.getTerrainHeight(worldX, worldZ);

        // Must be at least 3 blocks above sea level (no beach trees)
        if (height <= WorldConstants.SEA_LEVEL + 2) return false;

        // Check that the surface block is grass
        int surfBlock = chunk.getBlock(lx, height, lz);
        if (surfBlock != Blocks.GRASS.id()) return false;

        // Check for nearby water/sand (no trees on beach edges)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz2 = -1; dz2 <= 1; dz2++) {
                int nh = context.getTerrainHeight(worldX + dx, worldZ + dz2);
                if (nh <= WorldConstants.SEA_LEVEL + 1) return false;
            }
        }

        // Check slope
        int hN = context.getTerrainHeight(worldX, worldZ - 1);
        int hS = context.getTerrainHeight(worldX, worldZ + 1);
        int hE = context.getTerrainHeight(worldX + 1, worldZ);
        int hW = context.getTerrainHeight(worldX - 1, worldZ);

        int maxSlope = Math.max(
            Math.max(Math.abs(height - hN), Math.abs(height - hS)),
            Math.max(Math.abs(height - hE), Math.abs(height - hW))
        );

        if (maxSlope > config.treeSlopeMax) return false;

        // Check that space above is clear (at least trunk height + 2)
        int trunkHeight = config.treeMinTrunk + rng.nextInt(
            config.treeMaxTrunk - config.treeMinTrunk + 1);
        int topY = height + 1 + trunkHeight + 3;
        if (topY >= WorldConstants.WORLD_HEIGHT) return false;

        for (int y = height + 1; y <= height + 1 + trunkHeight; y++) {
            if (chunk.getBlock(lx, y, lz) != Blocks.AIR.id()) return false;
        }

        // Place the tree
        placeTree(chunk, lx, height + 1, lz, trunkHeight);
        return true;
    }

    /**
     * Place a simple oak tree at the given local position.
     * Trunk of LOG blocks, topped with a LEAVES canopy.
     * Classic tree shape: 4 layers of leaves.
     */
    private void placeTree(Chunk chunk, int x, int baseY, int z, int trunkHeight) {
        int topY = baseY + trunkHeight;
        if (topY + 3 >= WorldConstants.WORLD_HEIGHT) return;

        // Place trunk
        for (int y = baseY; y < topY; y++) {
            chunk.setBlock(x, y, z, Blocks.LOG.id());
        }

        // Place leaf canopy — Classic InfDev style (4 layers)
        int leafBase = topY - 1;

        // Bottom two layers: 5x5 with corners randomly removed
        placeLeafLayer(chunk, x, leafBase, z, 2, true);
        placeLeafLayer(chunk, x, leafBase + 1, z, 2, true);

        // Top layer: 3x3 cross
        placeLeafLayer(chunk, x, leafBase + 2, z, 1, false);

        // Tip: single block on top
        setLeaf(chunk, x, leafBase + 3, z);
    }

    /**
     * Place a horizontal layer of leaves centered at (cx, y, cz).
     */
    private void placeLeafLayer(Chunk chunk, int cx, int y, int cz,
                                int radius, boolean removeCorners) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (removeCorners && Math.abs(dx) == radius && Math.abs(dz) == radius) {
                    continue;
                }

                int lx = cx + dx;
                int lz = cz + dz;
                setLeaf(chunk, lx, y, lz);
            }
        }
    }

    /** Set a leaf block if within chunk bounds and the position is air. */
    private void setLeaf(Chunk chunk, int lx, int y, int lz) {
        if (lx < 0 || lx >= WorldConstants.CHUNK_SIZE ||
            lz < 0 || lz >= WorldConstants.CHUNK_SIZE ||
            y < 0 || y >= WorldConstants.WORLD_HEIGHT) {
            return;
        }

        if (chunk.getBlock(lx, y, lz) == Blocks.AIR.id()) {
            chunk.setBlock(lx, y, lz, Blocks.LEAVES.id());
        }
    }
}
