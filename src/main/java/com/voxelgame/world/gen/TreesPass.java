package com.voxelgame.world.gen;

import com.voxelgame.math.RNG;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.WorldConstants;

/**
 * Enhanced tree placement pass with variety.
 *
 * Tree types (all Infdev 611 style, just more interesting):
 * 1. STANDARD:  Classic 4-6 trunk, 5x5→3x3 canopy. The bread-and-butter tree.
 * 2. TALL:      7-10 trunk, narrow canopy at top. Occasional sentinel trees.
 * 3. BUSHY:     4-5 trunk, wide 7x7 bottom canopy. Fat and round.
 * 4. SLIM:      5-7 trunk, narrow 3x3 canopy only. Birch-like silhouette.
 * 5. BRANCHING: 6-8 trunk with 1-2 log branches sticking out, each with small canopy.
 *
 * Uses patch-based placement (InfDev style) with density multiplier from GenConfig.
 * Respects the treeDensityMultiplier for different presets.
 */
public class TreesPass implements GenPipeline.GenerationPass {

    /** Tree shape variants. */
    private enum TreeType {
        STANDARD, TALL, BUSHY, SLIM, BRANCHING
    }

    @Override
    public void apply(Chunk chunk, GenContext context) {
        GenConfig config = context.getConfig();
        double densityMult = config.treeDensityMultiplier;
        if (densityMult <= 0) return;

        RNG rng = context.chunkRNG(chunk.getPos().x() * 7, chunk.getPos().z() * 13);
        int chunkWorldX = chunk.getPos().worldX();
        int chunkWorldZ = chunk.getPos().worldZ();

        // Determine forest density using noise (if available)
        int chunkCenterX = chunkWorldX + WorldConstants.CHUNK_SIZE / 2;
        int chunkCenterZ = chunkWorldZ + WorldConstants.CHUNK_SIZE / 2;

        double forestDensity = 0;
        if (context.getForestNoise() != null) {
            forestDensity = context.getForestNoise().eval2D(
                chunkCenterX * 0.005, chunkCenterZ * 0.005);
        }

        // For flat worlds, use a simpler density model
        if (config.flatWorld) {
            int patches = rng.nextDouble() < config.treePatchChance * densityMult ? 1 : 0;
            for (int p = 0; p < patches; p++) {
                placePatch(chunk, context, rng, config, chunkWorldX, chunkWorldZ);
            }
            return;
        }

        // Barren area: very sparse
        if (forestDensity < -0.3) {
            if (rng.nextDouble() < 0.15 * densityMult) {
                placeSingleTree(chunk, context, rng, config, chunkWorldX, chunkWorldZ);
            }
            return;
        }

        // Number of tree patches scales with forest density and density multiplier
        int numPatches;
        if (forestDensity > config.forestNoiseThreshold + 0.3) {
            numPatches = (int) ((2 + rng.nextInt(3)) * densityMult);
        } else if (forestDensity > config.forestNoiseThreshold) {
            numPatches = (int) ((1 + rng.nextInt(2)) * densityMult);
        } else {
            numPatches = rng.nextDouble() < config.treePatchChance * densityMult ? 1 : 0;
        }

        for (int patch = 0; patch < numPatches; patch++) {
            placePatch(chunk, context, rng, config, chunkWorldX, chunkWorldZ);
        }
    }

    private void placePatch(Chunk chunk, GenContext context, RNG rng,
                            GenConfig config, int chunkWorldX, int chunkWorldZ) {
        int margin = config.treeEdgeMargin;
        int usableSize = WorldConstants.CHUNK_SIZE - margin * 2;
        if (usableSize <= 0) return;

        int centerLX = margin + rng.nextInt(usableSize);
        int centerLZ = margin + rng.nextInt(usableSize);

        int treesPlaced = 0;
        int maxTrees = 5 + rng.nextInt(config.treePatchAttempts - 4);

        for (int attempt = 0; attempt < config.treePatchAttempts * 2 && treesPlaced < maxTrees; attempt++) {
            int lx = centerLX + rng.nextInt(config.treePatchSpread * 2 + 1) - config.treePatchSpread;
            int lz = centerLZ + rng.nextInt(config.treePatchSpread * 2 + 1) - config.treePatchSpread;

            lx = Math.max(margin, Math.min(lx, WorldConstants.CHUNK_SIZE - margin - 1));
            lz = Math.max(margin, Math.min(lz, WorldConstants.CHUNK_SIZE - margin - 1));

            if (tryPlaceTree(chunk, context, rng, config, chunkWorldX, chunkWorldZ, lx, lz)) {
                treesPlaced++;
            }
        }
    }

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

    private boolean tryPlaceTree(Chunk chunk, GenContext context, RNG rng,
                                  GenConfig config, int chunkWorldX, int chunkWorldZ,
                                  int lx, int lz) {
        int height = findGrassHeight(chunk, lx, lz);
        if (height < 0) return false;

        // Use configurable sea level
        int seaLevel = config.seaLevel;
        // For flat worlds, allow trees at any height above the surface
        if (!config.flatWorld && height <= seaLevel + 2) return false;

        // Choose tree type based on RNG
        TreeType type = chooseTreeType(rng);
        int trunkHeight = getTrunkHeight(type, rng);

        int topY = height + 1 + trunkHeight + 4;
        if (topY >= WorldConstants.WORLD_HEIGHT) return false;

        // Check clear space above
        for (int y = height + 1; y <= height + 1 + trunkHeight; y++) {
            if (chunk.getBlock(lx, y, lz) != Blocks.AIR.id()) return false;
        }

        // Place the tree based on type
        placeTreeByType(chunk, lx, height + 1, lz, trunkHeight, type, rng);
        return true;
    }

    /**
     * Choose a tree type. Distribution:
     * 50% standard, 15% tall, 15% bushy, 10% slim, 10% branching
     */
    private TreeType chooseTreeType(RNG rng) {
        int roll = rng.nextInt(100);
        if (roll < 50) return TreeType.STANDARD;
        if (roll < 65) return TreeType.TALL;
        if (roll < 80) return TreeType.BUSHY;
        if (roll < 90) return TreeType.SLIM;
        return TreeType.BRANCHING;
    }

    /** Get trunk height range for each type. */
    private int getTrunkHeight(TreeType type, RNG rng) {
        return switch (type) {
            case STANDARD  -> 4 + rng.nextInt(3);  // 4-6
            case TALL      -> 7 + rng.nextInt(4);  // 7-10
            case BUSHY     -> 4 + rng.nextInt(2);  // 4-5
            case SLIM      -> 5 + rng.nextInt(3);  // 5-7
            case BRANCHING -> 6 + rng.nextInt(3);  // 6-8
        };
    }

    // ========================================================================
    // Tree placement methods — each creates a distinct silhouette
    // ========================================================================

    private void placeTreeByType(Chunk chunk, int x, int baseY, int z,
                                  int trunkHeight, TreeType type, RNG rng) {
        switch (type) {
            case STANDARD  -> placeStandardTree(chunk, x, baseY, z, trunkHeight, rng);
            case TALL      -> placeTallTree(chunk, x, baseY, z, trunkHeight, rng);
            case BUSHY     -> placeBushyTree(chunk, x, baseY, z, trunkHeight, rng);
            case SLIM      -> placeSlimTree(chunk, x, baseY, z, trunkHeight, rng);
            case BRANCHING -> placeBranchingTree(chunk, x, baseY, z, trunkHeight, rng);
        }
    }

    /**
     * STANDARD tree: Classic Infdev oak.
     * Trunk + 4-layer canopy (5x5 → 5x5 → 3x3 → 1x1 tip).
     * Some randomness: corners sometimes included/excluded.
     */
    private void placeStandardTree(Chunk chunk, int x, int baseY, int z,
                                    int trunkHeight, RNG rng) {
        int topY = baseY + trunkHeight;
        if (topY + 3 >= WorldConstants.WORLD_HEIGHT) return;

        // Trunk
        for (int y = baseY; y < topY; y++) {
            chunk.setBlock(x, y, z, Blocks.LOG.id());
        }

        // Bottom two layers: 5x5 with random corner removal
        int leafBase = topY - 1;
        placeLeafLayer(chunk, x, leafBase, z, 2, rng, 0.6);
        placeLeafLayer(chunk, x, leafBase + 1, z, 2, rng, 0.5);

        // Top layer: 3x3 cross-ish
        placeLeafLayer(chunk, x, leafBase + 2, z, 1, rng, 0.3);

        // Tip
        setLeaf(chunk, x, leafBase + 3, z);
    }

    /**
     * TALL tree: Sentinel/spruce-like. Long trunk, narrow canopy stacked at top.
     * Creates a tall narrow silhouette visible from a distance.
     */
    private void placeTallTree(Chunk chunk, int x, int baseY, int z,
                                int trunkHeight, RNG rng) {
        int topY = baseY + trunkHeight;
        if (topY + 4 >= WorldConstants.WORLD_HEIGHT) return;

        // Long trunk
        for (int y = baseY; y < topY; y++) {
            chunk.setBlock(x, y, z, Blocks.LOG.id());
        }
        // Extend trunk into canopy
        chunk.setBlock(x, topY, z, Blocks.LOG.id());
        chunk.setBlock(x, topY + 1, z, Blocks.LOG.id());

        // Narrow tiered canopy (3 tiers)
        int leafBase = topY - 2;
        // Tier 1 (widest): radius 2, sparse
        placeLeafLayer(chunk, x, leafBase, z, 2, rng, 0.7);
        // Tier 2: radius 1
        placeLeafLayer(chunk, x, leafBase + 1, z, 2, rng, 0.6);
        // Tier 3: radius 1
        placeLeafLayer(chunk, x, leafBase + 2, z, 1, rng, 0.3);
        // Tier 4: radius 1
        placeLeafLayer(chunk, x, leafBase + 3, z, 1, rng, 0.2);
        // Top tips
        setLeaf(chunk, x, leafBase + 4, z);
        // Cross tips
        if (rng.nextBoolean()) setLeaf(chunk, x + 1, leafBase + 4, z);
        if (rng.nextBoolean()) setLeaf(chunk, x - 1, leafBase + 4, z);
        if (rng.nextBoolean()) setLeaf(chunk, x, leafBase + 4, z + 1);
        if (rng.nextBoolean()) setLeaf(chunk, x, leafBase + 4, z - 1);
    }

    /**
     * BUSHY tree: Wide, round canopy. Short trunk.
     * Creates a fat mushroom-like shape.
     */
    private void placeBushyTree(Chunk chunk, int x, int baseY, int z,
                                 int trunkHeight, RNG rng) {
        int topY = baseY + trunkHeight;
        if (topY + 3 >= WorldConstants.WORLD_HEIGHT) return;

        // Short trunk
        for (int y = baseY; y < topY; y++) {
            chunk.setBlock(x, y, z, Blocks.LOG.id());
        }

        // Wide canopy: 3 layers
        int leafBase = topY - 2;
        // Bottom: radius 3 (7x7 with corners removed)
        placeLeafLayer(chunk, x, leafBase, z, 3, rng, 0.7);
        // Middle: radius 3 (denser)
        placeLeafLayer(chunk, x, leafBase + 1, z, 3, rng, 0.5);
        // Top: radius 2
        placeLeafLayer(chunk, x, leafBase + 2, z, 2, rng, 0.5);
        // Crown
        placeLeafLayer(chunk, x, leafBase + 3, z, 1, rng, 0.3);
    }

    /**
     * SLIM tree: Narrow, birch-like. Minimal canopy.
     * Creates a vertical accent in forests.
     */
    private void placeSlimTree(Chunk chunk, int x, int baseY, int z,
                                int trunkHeight, RNG rng) {
        int topY = baseY + trunkHeight;
        if (topY + 3 >= WorldConstants.WORLD_HEIGHT) return;

        // Trunk
        for (int y = baseY; y < topY; y++) {
            chunk.setBlock(x, y, z, Blocks.LOG.id());
        }

        // Narrow canopy: only 3x3 layers
        int leafBase = topY - 1;
        placeLeafLayer(chunk, x, leafBase, z, 1, rng, 0.2);
        placeLeafLayer(chunk, x, leafBase + 1, z, 1, rng, 0.2);
        setLeaf(chunk, x, leafBase + 2, z);

        // Random single-block leaf extensions at trunk mid-height
        int midY = baseY + trunkHeight / 2;
        if (rng.nextBoolean()) setLeaf(chunk, x + 1, midY, z);
        if (rng.nextBoolean()) setLeaf(chunk, x - 1, midY, z);
        if (rng.nextBoolean()) setLeaf(chunk, x, midY, z + 1);
        if (rng.nextBoolean()) setLeaf(chunk, x, midY, z - 1);
    }

    /**
     * BRANCHING tree: Trunk with 1-2 log branches, each ending in a small leaf cluster.
     * Creates the most complex and interesting silhouette.
     */
    private void placeBranchingTree(Chunk chunk, int x, int baseY, int z,
                                     int trunkHeight, RNG rng) {
        int topY = baseY + trunkHeight;
        if (topY + 3 >= WorldConstants.WORLD_HEIGHT) return;

        // Main trunk
        for (int y = baseY; y < topY; y++) {
            chunk.setBlock(x, y, z, Blocks.LOG.id());
        }

        // Main canopy at top (standard-ish)
        int leafBase = topY - 1;
        placeLeafLayer(chunk, x, leafBase, z, 2, rng, 0.6);
        placeLeafLayer(chunk, x, leafBase + 1, z, 2, rng, 0.5);
        placeLeafLayer(chunk, x, leafBase + 2, z, 1, rng, 0.3);
        setLeaf(chunk, x, leafBase + 3, z);

        // Place 1-2 branches at different heights
        int numBranches = 1 + rng.nextInt(2);
        for (int b = 0; b < numBranches; b++) {
            int branchY = baseY + 2 + rng.nextInt(Math.max(1, trunkHeight - 3));
            int dx = rng.nextBoolean() ? 1 : -1;
            int dz = rng.nextBoolean() ? 1 : -1;

            // Pick a direction (only extend in one axis for simplicity)
            if (rng.nextBoolean()) {
                // Extend in X
                int branchX = x + dx;
                if (branchX >= 0 && branchX < WorldConstants.CHUNK_SIZE) {
                    chunk.setBlock(branchX, branchY, z, Blocks.LOG.id());
                    int bx2 = branchX + dx;
                    if (bx2 >= 0 && bx2 < WorldConstants.CHUNK_SIZE) {
                        chunk.setBlock(bx2, branchY, z, Blocks.LOG.id());
                        // Small leaf cluster at branch tip
                        placeBranchLeaves(chunk, bx2, branchY, z, rng);
                    } else {
                        placeBranchLeaves(chunk, branchX, branchY, z, rng);
                    }
                }
            } else {
                // Extend in Z
                int branchZ = z + dz;
                if (branchZ >= 0 && branchZ < WorldConstants.CHUNK_SIZE) {
                    chunk.setBlock(x, branchY, branchZ, Blocks.LOG.id());
                    int bz2 = branchZ + dz;
                    if (bz2 >= 0 && bz2 < WorldConstants.CHUNK_SIZE) {
                        chunk.setBlock(x, branchY, bz2, Blocks.LOG.id());
                        placeBranchLeaves(chunk, x, branchY, bz2, rng);
                    } else {
                        placeBranchLeaves(chunk, x, branchY, branchZ, rng);
                    }
                }
            }
        }
    }

    /** Place a small cluster of leaves around a branch tip. */
    private void placeBranchLeaves(Chunk chunk, int cx, int cy, int cz, RNG rng) {
        // Small 3x3x2 leaf ball
        setLeaf(chunk, cx, cy + 1, cz);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    setLeaf(chunk, cx, cy + 1, cz); // already set
                } else if (rng.nextInt(3) > 0) { // 66% chance
                    setLeaf(chunk, cx + dx, cy, cz + dz);
                    if (rng.nextBoolean()) {
                        setLeaf(chunk, cx + dx, cy + 1, cz + dz);
                    }
                }
            }
        }
    }

    // ========================================================================
    // Shared helpers
    // ========================================================================

    /**
     * Place a horizontal layer of leaves with random corner removal.
     * @param cornerChance probability of REMOVING a corner block (0 = always keep, 1 = always remove)
     */
    private void placeLeafLayer(Chunk chunk, int cx, int y, int cz,
                                int radius, RNG rng, double cornerChance) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // Corner check — randomly remove for organic shape
                if (Math.abs(dx) == radius && Math.abs(dz) == radius) {
                    if (rng.nextDouble() < cornerChance) continue;
                }
                // For larger radii, also randomly thin the outer ring
                if (radius >= 3 && (Math.abs(dx) == radius || Math.abs(dz) == radius)) {
                    if (rng.nextDouble() < 0.3) continue;
                }
                setLeaf(chunk, cx + dx, y, cz + dz);
            }
        }
    }

    private int findGrassHeight(Chunk chunk, int lx, int lz) {
        for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
            if (chunk.getBlock(lx, y, lz) == Blocks.GRASS.id()) {
                return y;
            }
        }
        return -1;
    }

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
