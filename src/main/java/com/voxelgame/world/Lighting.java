package com.voxelgame.world;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Unified lighting system with classic Minecraft-style sky light.
 *
 * Sky Light: BFS flood-fill from sky-exposed blocks.
 * - Column pass: sunlight (level 15) propagates straight down through air
 * - BFS pass: light spreads laterally in all 6 directions, -1 per block
 * - Water reduces light by 3 per block, leaves by 1
 * - This illuminates cave entrances, overhangs, and covered areas
 *
 * Block Light: BFS flood-fill from emissive blocks (torches, etc.).
 * Phase 4: RGB propagation with nonlinear falloff.
 */
public class Lighting {

    private static final int MAX_LIGHT = 15;

    // 6 directions: +X, -X, +Y, -Y, +Z, -Z
    private static final int[][] DIRS = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };

    // ========================================================================
    // SKY LIGHT - Classic Minecraft BFS flood-fill
    // ========================================================================

    /**
     * Compute sky light for a newly generated chunk using classic Minecraft approach.
     *
     * Phase 1 (Column): Sunlight (level 15) propagates straight down through air.
     *   Water reduces by 3/block, leaves by 1/block. Opaque blocks stop all light.
     *
     * Phase 2 (BFS): Light spreads laterally in all 6 directions, -1 per block.
     *   This illuminates cave entrances, overhangs, and covered areas.
     */
    public static void computeInitialSkyVisibility(Chunk chunk, World world) {
        ChunkPos cPos = chunk.getPos();
        int cx = cPos.x() * WorldConstants.CHUNK_SIZE;
        int cz = cPos.z() * WorldConstants.CHUNK_SIZE;
        int CS = WorldConstants.CHUNK_SIZE;
        int WH = WorldConstants.WORLD_HEIGHT;

        // Phase 1: Column pass - sunlight propagates straight down from sky
        // Downward through transparent: stays at 15. Opaque blocks: 0. Water: -3/block.
        for (int x = 0; x < CS; x++) {
            for (int z = 0; z < CS; z++) {
                int level = MAX_LIGHT;
                for (int y = WH - 1; y >= 0; y--) {
                    int blockId = chunk.getBlock(x, y, z);
                    Block block = Blocks.get(blockId);
                    if (isOpaque(block)) {
                        chunk.setSkyLight(x, y, z, 0);
                        level = 0;
                    } else {
                        int opacity = getSkyLightOpacity(blockId);
                        level = Math.max(0, level - opacity);
                        chunk.setSkyLight(x, y, z, level);
                    }
                }
            }
        }

        // Phase 2: BFS flood fill - propagate light laterally (and up) within this chunk.
        // Seeds come from TWO sources:
        //   A) Internal: lit blocks whose in-chunk neighbor is darker and non-opaque
        //   B) Cross-chunk: neighbor chunks' edge blocks that should propagate into this chunk
        Queue<int[]> bfsQueue = new ArrayDeque<>();

        // 2A: Internal seeds
        for (int x = 0; x < CS; x++) {
            for (int z = 0; z < CS; z++) {
                for (int y = 0; y < WH; y++) {
                    int level = chunk.getSkyLight(x, y, z);
                    if (level <= 1) continue;

                    boolean needsSeed = false;
                    for (int[] dir : DIRS) {
                        int nx = x + dir[0], ny = y + dir[1], nz = z + dir[2];
                        if (nx < 0 || nx >= CS || nz < 0 || nz >= CS) continue;
                        if (ny < 0 || ny >= WH) continue;

                        int nLevel = chunk.getSkyLight(nx, ny, nz);
                        if (nLevel < level - 1) {
                            int nBlockId = chunk.getBlock(nx, ny, nz);
                            if (!isOpaque(Blocks.get(nBlockId))) {
                                needsSeed = true;
                                break;
                            }
                        }
                    }
                    if (needsSeed) {
                        bfsQueue.add(new int[]{cx + x, y, cz + z, level});
                    }
                }
            }
        }

        // 2B: Cross-chunk seeds - check neighboring chunks' edge blocks.
        // If a neighbor's edge block has light level L > 1, and the adjacent block
        // in THIS chunk is non-opaque with light < L-1, seed from the neighbor.
        seedFromNeighborEdge(bfsQueue, world, chunk, cx, cz, CS, WH);

        // Phase 3: BFS lateral propagation - confined to this chunk
        propagateSkyLightBFSLocal(bfsQueue, world, cx, cz);

        chunk.setLightDirty(false);
    }

    /**
     * Seed BFS queue from loaded neighboring chunks' edge blocks.
     * For each of the 4 cardinal neighbors, scan their edge facing this chunk.
     * If a neighbor edge block has light > 1 and the corresponding block in this
     * chunk is non-opaque and darker, add the neighbor block as a seed.
     */
    private static void seedFromNeighborEdge(Queue<int[]> bfsQueue, World world,
                                              Chunk chunk, int cx, int cz, int CS, int WH) {
        // 4 cardinal directions: +X, -X, +Z, -Z
        int[][] neighborOffsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] off : neighborOffsets) {
            int ncx = Math.floorDiv(cx, CS) + off[0];
            int ncz = Math.floorDiv(cz, CS) + off[1];
            Chunk neighbor = world.getChunk(ncx, ncz);
            if (neighbor == null) continue;

            // Determine which edge to scan
            // off={1,0}: neighbor is to +X, scan neighbor's x=0 edge, our x=CS-1
            // off={-1,0}: neighbor is to -X, scan neighbor's x=CS-1 edge, our x=0
            // off={0,1}: neighbor is to +Z, scan neighbor's z=0 edge, our z=CS-1
            // off={0,-1}: neighbor is to -Z, scan neighbor's z=CS-1 edge, our z=0
            for (int a = 0; a < CS; a++) {
                for (int y = 0; y < WH; y++) {
                    int nLocalX, nLocalZ, ourLocalX, ourLocalZ;
                    if (off[0] != 0) {
                        // X-axis neighbor
                        nLocalX = (off[0] == 1) ? 0 : CS - 1;
                        nLocalZ = a;
                        ourLocalX = (off[0] == 1) ? CS - 1 : 0;
                        ourLocalZ = a;
                    } else {
                        // Z-axis neighbor
                        nLocalX = a;
                        nLocalZ = (off[1] == 1) ? 0 : CS - 1;
                        ourLocalX = a;
                        ourLocalZ = (off[1] == 1) ? CS - 1 : 0;
                    }

                    int neighborLevel = neighbor.getSkyLight(nLocalX, y, nLocalZ);
                    if (neighborLevel <= 1) continue;

                    // Check if our adjacent block is non-opaque and darker
                    int ourBlockId = chunk.getBlock(ourLocalX, y, ourLocalZ);
                    if (isOpaque(Blocks.get(ourBlockId))) continue;

                    int ourLevel = chunk.getSkyLight(ourLocalX, y, ourLocalZ);
                    int opacity = getSkyLightOpacity(ourBlockId);
                    int propagated = neighborLevel - 1 - opacity;
                    if (propagated > ourLevel && propagated > 0) {
                        // Update our edge block and seed BFS from it (world coords in THIS chunk)
                        chunk.setSkyLight(ourLocalX, y, ourLocalZ, propagated);
                        bfsQueue.add(new int[]{cx + ourLocalX, y, cz + ourLocalZ, propagated});
                    }
                }
            }
        }
    }

    /**
     * Propagate sky light from a source chunk's edge into an adjacent neighbor chunk.
     * Called when a new chunk loads to update already-loaded neighbors with the new light.
     *
     * @param source the newly loaded chunk with fresh lighting
     * @param neighbor the already-loaded neighbor to update
     * @param world world access
     * @return true if any light was propagated (neighbor needs remesh)
     */
    public static boolean propagateEdgeLight(Chunk source, Chunk neighbor, World world) {
        int CS = WorldConstants.CHUNK_SIZE;
        int WH = WorldConstants.WORLD_HEIGHT;
        ChunkPos sp = source.getPos();
        ChunkPos np = neighbor.getPos();

        // Determine direction: source -> neighbor
        int dx = np.x() - sp.x();
        int dz = np.z() - sp.z();
        if (Math.abs(dx) + Math.abs(dz) != 1) return false; // Not adjacent

        int ncx = np.x() * CS;
        int ncz = np.z() * CS;

        // Source edge local coords and neighbor edge local coords
        int srcEdgeX, srcEdgeZ, nbrEdgeX, nbrEdgeZ;
        boolean xAxis = dx != 0;

        Queue<int[]> bfsQueue = new ArrayDeque<>();
        boolean changed = false;

        for (int a = 0; a < CS; a++) {
            for (int y = 0; y < WH; y++) {
                if (xAxis) {
                    srcEdgeX = (dx == 1) ? CS - 1 : 0; // source's edge facing neighbor
                    srcEdgeZ = a;
                    nbrEdgeX = (dx == 1) ? 0 : CS - 1; // neighbor's edge facing source
                    nbrEdgeZ = a;
                } else {
                    srcEdgeX = a;
                    srcEdgeZ = (dz == 1) ? CS - 1 : 0;
                    nbrEdgeX = a;
                    nbrEdgeZ = (dz == 1) ? 0 : CS - 1;
                }

                int srcLevel = source.getSkyLight(srcEdgeX, y, srcEdgeZ);
                if (srcLevel <= 1) continue;

                int nbrBlockId = neighbor.getBlock(nbrEdgeX, y, nbrEdgeZ);
                if (isOpaque(Blocks.get(nbrBlockId))) continue;

                int nbrLevel = neighbor.getSkyLight(nbrEdgeX, y, nbrEdgeZ);
                int opacity = getSkyLightOpacity(nbrBlockId);
                int propagated = srcLevel - 1 - opacity;

                if (propagated > nbrLevel && propagated > 0) {
                    neighbor.setSkyLight(nbrEdgeX, y, nbrEdgeZ, propagated);
                    bfsQueue.add(new int[]{ncx + nbrEdgeX, y, ncz + nbrEdgeZ, propagated});
                    changed = true;
                }
            }
        }

        if (!bfsQueue.isEmpty()) {
            propagateSkyLightBFSLocal(bfsQueue, world, ncx, ncz);
        }
        return changed;
    }

    /**
     * Backward-compatible overload for callers without World access.
     * Only does column pass (no lateral BFS propagation).
     */
    public static void computeInitialSkyVisibility(Chunk chunk) {
        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                int level = MAX_LIGHT;
                for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
                    int blockId = chunk.getBlock(x, y, z);
                    Block block = Blocks.get(blockId);
                    if (isOpaque(block)) {
                        chunk.setSkyLight(x, y, z, 0);
                        level = 0;
                    } else {
                        int opacity = getSkyLightOpacity(blockId);
                        level = Math.max(0, level - opacity);
                        chunk.setSkyLight(x, y, z, level);
                    }
                }
            }
        }
        chunk.setLightDirty(false);
    }

    /**
     * BFS flood-fill sky light propagation, confined to the chunk area.
     * Prevents unbounded cross-chunk cascading that causes OOM.
     * Cross-chunk propagation happens when neighboring chunks load.
     */
    private static int propagateSkyLightBFSLocal(Queue<int[]> queue, World world, int cx, int cz) {
        int updateCount = 0;
        // Strictly within chunk bounds - prevents infinite loop with unloaded neighbors
        // (unloaded chunks: setSkyVisibility does nothing, getSkyVisibility returns 0,
        //  so newLevel > 0 is always true = infinite re-enqueue)
        int minX = cx;
        int maxX = cx + WorldConstants.CHUNK_SIZE - 1;
        int minZ = cz;
        int maxZ = cz + WorldConstants.CHUNK_SIZE - 1;

        while (!queue.isEmpty()) {
            int[] entry = queue.poll();
            int wx = entry[0];
            int wy = entry[1];
            int wz = entry[2];
            int level = entry[3];

            for (int[] dir : DIRS) {
                int nx = wx + dir[0];
                int ny = wy + dir[1];
                int nz = wz + dir[2];

                // Stay within chunk boundaries (+ 1 block margin)
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;
                if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

                int neighborBlock = world.getBlock(nx, ny, nz);
                Block nBlock = Blocks.get(neighborBlock);

                if (isOpaque(nBlock)) continue;

                // Reduce by 1 (standard) + block-specific opacity
                int opacity = getSkyLightOpacity(neighborBlock);
                int newLevel = level - 1 - opacity;
                if (newLevel <= 0) continue;

                // Only update if we can improve the current value
                float currentVis = world.getSkyVisibility(nx, ny, nz);
                int currentLevel = Math.round(currentVis * 15.0f);

                if (newLevel > currentLevel) {
                    world.setSkyVisibility(nx, ny, nz, newLevel / 15.0f);
                    queue.add(new int[]{nx, ny, nz, newLevel});
                    updateCount++;
                }
            }
        }
        return updateCount;
    }

    /**
     * Get sky light opacity for a block (how much it reduces sky light passing through).
     * Classic Minecraft-style: water = 3, leaves = 1, air/glass = 0.
     */
    private static int getSkyLightOpacity(int blockId) {
        if (blockId == 0) return 0; // Air
        if (Blocks.isWater(blockId)) return 3; // Water heavily blocks
        if (blockId == Blocks.LEAVES.id()) return 1; // Leaves slightly block
        return 0; // Glass and other transparent blocks
    }

    /**
     * Update sky light after a block is removed (broken).
     * Recomputes column sky light and does local BFS propagation.
     * Returns affected chunk positions for mesh rebuild.
     */
    public static Set<ChunkPos> onBlockRemoved(World world, int wx, int wy, int wz) {
        Set<ChunkPos> affectedChunks = new HashSet<>();

        // Recompute column sky light from top down
        int level = MAX_LIGHT;
        Queue<int[]> bfsQueue = new ArrayDeque<>();
        boolean prevOpaque = false;

        for (int y = WorldConstants.WORLD_HEIGHT - 1; y >= 0; y--) {
            int blockId = world.getBlock(wx, y, wz);
            Block block = Blocks.get(blockId);

            int oldLevel = Math.round(world.getSkyVisibility(wx, y, wz) * 15.0f);

            if (isOpaque(block)) {
                // Seed from block above if it was a boundary
                if (!prevOpaque && level > 1 && y < WorldConstants.WORLD_HEIGHT - 1) {
                    bfsQueue.add(new int[]{wx, y + 1, wz, oldLevel > 0 ? oldLevel : level});
                }
                level = 0;
                prevOpaque = true;
            } else {
                int opacity = getSkyLightOpacity(blockId);
                level = Math.max(0, level - opacity);
                if (prevOpaque && level > 1) {
                    bfsQueue.add(new int[]{wx, y, wz, level});
                }
                prevOpaque = false;
            }

            if (level != oldLevel) {
                world.setSkyVisibility(wx, y, wz, level / 15.0f);
                addAffectedChunk(affectedChunks, wx, y, wz);
            }
        }

        // Seed from neighbors of the removed block
        for (int[] dir : DIRS) {
            int nx = wx + dir[0];
            int ny = wy + dir[1];
            int nz = wz + dir[2];
            if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;
            int nLevel = Math.round(world.getSkyVisibility(nx, ny, nz) * 15.0f);
            if (nLevel > 1) {
                bfsQueue.add(new int[]{nx, ny, nz, nLevel});
            }
        }

        // Local BFS around the affected area
        int chunkCx = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE) * WorldConstants.CHUNK_SIZE;
        int chunkCz = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE) * WorldConstants.CHUNK_SIZE;
        propagateSkyLightBFSLocal(bfsQueue, world, chunkCx, chunkCz);
        addAffectedChunk(affectedChunks, wx, wy, wz);

        return affectedChunks;
    }

    /**
     * Update sky light after a block is placed.
     * If opaque, clears sky light below and re-propagates from neighbors.
     * Returns affected chunk positions for mesh rebuild.
     */
    public static Set<ChunkPos> onBlockPlaced(World world, int wx, int wy, int wz) {
        Set<ChunkPos> affectedChunks = new HashSet<>();

        int blockId = world.getBlock(wx, wy, wz);
        Block block = Blocks.get(blockId);

        if (isOpaque(block)) {
            // Opaque block placed - clear sky light at this position and below
            world.setSkyVisibility(wx, wy, wz, 0.0f);
            addAffectedChunk(affectedChunks, wx, wy, wz);

            for (int y = wy - 1; y >= 0; y--) {
                int belowId = world.getBlock(wx, y, wz);
                Block belowBlock = Blocks.get(belowId);
                int currentLevel = Math.round(world.getSkyVisibility(wx, y, wz) * 15.0f);

                if (isOpaque(belowBlock)) {
                    break;
                }
                if (currentLevel > 0) {
                    world.setSkyVisibility(wx, y, wz, 0.0f);
                    addAffectedChunk(affectedChunks, wx, y, wz);
                } else {
                    break;
                }
            }

            // Re-propagate from immediate neighbors only (bounded)
            Queue<int[]> bfsQueue = new ArrayDeque<>();
            for (int[] dir : DIRS) {
                int nx = wx + dir[0];
                int ny = wy + dir[1];
                int nz = wz + dir[2];
                if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;
                int nLevel = Math.round(world.getSkyVisibility(nx, ny, nz) * 15.0f);
                if (nLevel > 1) {
                    bfsQueue.add(new int[]{nx, ny, nz, nLevel});
                }
            }
            int chunkCx = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE) * WorldConstants.CHUNK_SIZE;
            int chunkCz = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE) * WorldConstants.CHUNK_SIZE;
            propagateSkyLightBFSLocal(bfsQueue, world, chunkCx, chunkCz);
        } else {
            // Transparent block placed - compute column level at this position
            int level = MAX_LIGHT;
            for (int y = WorldConstants.WORLD_HEIGHT - 1; y > wy; y--) {
                int aboveId = world.getBlock(wx, y, wz);
                if (isOpaque(Blocks.get(aboveId))) {
                    level = 0;
                    break;
                }
                level = Math.max(0, level - getSkyLightOpacity(aboveId));
            }
            level = Math.max(0, level - getSkyLightOpacity(blockId));

            world.setSkyVisibility(wx, wy, wz, level / 15.0f);
            addAffectedChunk(affectedChunks, wx, wy, wz);

            if (level > 1) {
                Queue<int[]> bfsQueue = new ArrayDeque<>();
                bfsQueue.add(new int[]{wx, wy, wz, level});
                int chunkCx = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE) * WorldConstants.CHUNK_SIZE;
                int chunkCz = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE) * WorldConstants.CHUNK_SIZE;
                propagateSkyLightBFSLocal(bfsQueue, world, chunkCx, chunkCz);
            }
        }

        return affectedChunks;
    }

    // ========================================================================
    // BLOCK LIGHT SYSTEM - Phase 4: RGB propagation with nonlinear falloff
    // ========================================================================
    
    /** Nonlinear falloff factor: multiply by this each block traveled. */
    private static final float RGB_FALLOFF = 0.8f;
    
    /** Minimum light value to continue propagation (avoids infinite spread). */
    private static final float RGB_MIN_THRESHOLD = 0.01f;

    /**
     * Compute initial RGB block light for a newly generated chunk.
     * Scans for light-emitting blocks and propagates colored light from them.
     * Phase 4: Uses RGB values with nonlinear falloff instead of scalar 0-15.
     */
    public static void computeInitialBlockLight(Chunk chunk, World world) {
        ChunkPos cPos = chunk.getPos();
        int cx = cPos.x() * WorldConstants.CHUNK_SIZE;
        int cz = cPos.z() * WorldConstants.CHUNK_SIZE;

        // Clear existing RGB light in this chunk
        chunk.clearBlockLightRGB();
        
        // Queue stores: [wx, wy, wz, Float.floatToIntBits(r), Float.floatToIntBits(g), Float.floatToIntBits(b)]
        Queue<long[]> bfsQueue = new ArrayDeque<>();

        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                for (int y = 0; y < WorldConstants.WORLD_HEIGHT; y++) {
                    int blockId = chunk.getBlock(x, y, z);
                    float[] lightColor = LightEmitters.getLightColorRGB(blockId);
                    if (lightColor != null) {
                        chunk.setBlockLightRGB(x, y, z, lightColor[0], lightColor[1], lightColor[2]);
                        bfsQueue.add(new long[]{cx + x, y, cz + z, 
                            Float.floatToIntBits(lightColor[0]),
                            Float.floatToIntBits(lightColor[1]),
                            Float.floatToIntBits(lightColor[2])});
                    }
                }
            }
        }

        propagateBlockLightRGBBFS(bfsQueue, world);
    }

    /**
     * BFS flood-fill RGB block light propagation with nonlinear falloff.
     * Phase 4: Each channel attenuates by RGB_FALLOFF per block traveled.
     */
    private static void propagateBlockLightRGBBFS(Queue<long[]> queue, World world) {
        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            int wx = (int) entry[0];
            int wy = (int) entry[1];
            int wz = (int) entry[2];
            float r = Float.intBitsToFloat((int) entry[3]);
            float g = Float.intBitsToFloat((int) entry[4]);
            float b = Float.intBitsToFloat((int) entry[5]);

            for (int[] dir : DIRS) {
                int nx = wx + dir[0];
                int ny = wy + dir[1];
                int nz = wz + dir[2];

                if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

                int neighborBlock = world.getBlock(nx, ny, nz);
                Block nBlock = Blocks.get(neighborBlock);

                if (isOpaque(nBlock)) continue;

                // Nonlinear falloff: multiply by RGB_FALLOFF
                float extraReduction = getLightReductionFactor(nBlock);
                float falloff = RGB_FALLOFF * extraReduction;
                float nr = r * falloff;
                float ng = g * falloff;
                float nb = b * falloff;

                // Stop if all channels are too dim
                if (nr < RGB_MIN_THRESHOLD && ng < RGB_MIN_THRESHOLD && nb < RGB_MIN_THRESHOLD) continue;

                // Only update if we're brighter than current in any channel
                float[] current = world.getBlockLightRGB(nx, ny, nz);
                if (nr > current[0] || ng > current[1] || nb > current[2]) {
                    // Take max of each channel (allows multiple colored lights to blend)
                    float newR = Math.max(nr, current[0]);
                    float newG = Math.max(ng, current[1]);
                    float newB = Math.max(nb, current[2]);
                    world.setBlockLightRGB(nx, ny, nz, newR, newG, newB);
                    queue.add(new long[]{nx, ny, nz, 
                        Float.floatToIntBits(nr),
                        Float.floatToIntBits(ng),
                        Float.floatToIntBits(nb)});
                }
            }
        }
    }

    /**
     * Add block light when a light-emitting block is placed (e.g., torch).
     * Phase 4: Propagates RGB light with nonlinear falloff.
     */
    public static Set<ChunkPos> onLightSourcePlaced(World world, int wx, int wy, int wz) {
        Set<ChunkPos> affectedChunks = new HashSet<>();
        int blockId = world.getBlock(wx, wy, wz);
        float[] lightColor = LightEmitters.getLightColorRGB(blockId);
        if (lightColor == null) return affectedChunks;

        world.setBlockLightRGB(wx, wy, wz, lightColor[0], lightColor[1], lightColor[2]);
        addAffectedChunk(affectedChunks, wx, wy, wz);

        Queue<long[]> bfsQueue = new ArrayDeque<>();
        bfsQueue.add(new long[]{wx, wy, wz,
            Float.floatToIntBits(lightColor[0]),
            Float.floatToIntBits(lightColor[1]),
            Float.floatToIntBits(lightColor[2])});
        propagateBlockLightRGBBFSTracked(bfsQueue, world, affectedChunks);

        return affectedChunks;
    }

    /**
     * Remove block light when a light source is removed (e.g., torch broken).
     * Phase 4: Clears RGB light and re-propagates from remaining sources.
     */
    public static Set<ChunkPos> onLightSourceRemoved(World world, int wx, int wy, int wz, int oldEmission) {
        Set<ChunkPos> affectedChunks = new HashSet<>();
        if (oldEmission <= 0) return affectedChunks;

        // Calculate max radius that could have been affected
        // With 0.8 falloff: 0.8^n < 0.01 means n > log(0.01)/log(0.8) ≈ 21 blocks
        int maxRadius = 25;

        // Clear light in affected area and collect light sources for re-propagation
        Queue<long[]> reproQueue = new ArrayDeque<>();
        
        for (int dx = -maxRadius; dx <= maxRadius; dx++) {
            for (int dy = -maxRadius; dy <= maxRadius; dy++) {
                for (int dz = -maxRadius; dz <= maxRadius; dz++) {
                    int nx = wx + dx;
                    int ny = wy + dy;
                    int nz = wz + dz;
                    
                    if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;
                    
                    float[] rgb = world.getBlockLightRGB(nx, ny, nz);
                    if (rgb[0] > 0 || rgb[1] > 0 || rgb[2] > 0) {
                        // Check if this is a light source itself
                        int nBlockId = world.getBlock(nx, ny, nz);
                        float[] sourceColor = LightEmitters.getLightColorRGB(nBlockId);
                        
                        if (sourceColor != null && !(nx == wx && ny == wy && nz == wz)) {
                            // This is another light source - add to repro queue
                            reproQueue.add(new long[]{nx, ny, nz,
                                Float.floatToIntBits(sourceColor[0]),
                                Float.floatToIntBits(sourceColor[1]),
                                Float.floatToIntBits(sourceColor[2])});
                        }
                        
                        // Clear light at this position
                        world.setBlockLightRGB(nx, ny, nz, 0, 0, 0);
                        addAffectedChunk(affectedChunks, nx, ny, nz);
                    }
                }
            }
        }

        // Re-propagate from remaining light sources
        propagateBlockLightRGBBFSTracked(reproQueue, world, affectedChunks);

        return affectedChunks;
    }

    /**
     * BFS RGB block light propagation that tracks affected chunks.
     */
    private static void propagateBlockLightRGBBFSTracked(Queue<long[]> queue, World world, Set<ChunkPos> affected) {
        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            int wx = (int) entry[0];
            int wy = (int) entry[1];
            int wz = (int) entry[2];
            float r = Float.intBitsToFloat((int) entry[3]);
            float g = Float.intBitsToFloat((int) entry[4]);
            float b = Float.intBitsToFloat((int) entry[5]);

            for (int[] dir : DIRS) {
                int nx = wx + dir[0];
                int ny = wy + dir[1];
                int nz = wz + dir[2];

                if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

                int neighborBlock = world.getBlock(nx, ny, nz);
                Block nBlock = Blocks.get(neighborBlock);

                if (isOpaque(nBlock)) continue;

                float extraReduction = getLightReductionFactor(nBlock);
                float falloff = RGB_FALLOFF * extraReduction;
                float nr = r * falloff;
                float ng = g * falloff;
                float nb = b * falloff;

                if (nr < RGB_MIN_THRESHOLD && ng < RGB_MIN_THRESHOLD && nb < RGB_MIN_THRESHOLD) continue;

                float[] current = world.getBlockLightRGB(nx, ny, nz);
                if (nr > current[0] || ng > current[1] || nb > current[2]) {
                    float newR = Math.max(nr, current[0]);
                    float newG = Math.max(ng, current[1]);
                    float newB = Math.max(nb, current[2]);
                    world.setBlockLightRGB(nx, ny, nz, newR, newG, newB);
                    addAffectedChunk(affected, nx, ny, nz);
                    queue.add(new long[]{nx, ny, nz,
                        Float.floatToIntBits(nr),
                        Float.floatToIntBits(ng),
                        Float.floatToIntBits(nb)});
                }
            }
        }
    }

    // ========================================================================
    // LEGACY API - For backward compatibility during transition
    // ========================================================================

    /**
     * Legacy method - redirects to computeInitialSkyVisibility with World.
     * @deprecated Use {@link #computeInitialSkyVisibility(Chunk, World)} instead.
     */
    @Deprecated
    public static void computeInitialSkyLight(Chunk chunk, World world) {
        computeInitialSkyVisibility(chunk, world);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /** Check if a block is opaque (blocks light completely). */
    private static boolean isOpaque(Block block) {
        return block.solid() && !block.transparent();
    }

    /** Get light reduction factor (0-1 multiplier) for Phase 4 RGB propagation. */
    private static float getLightReductionFactor(Block block) {
        if (Blocks.isWater(block.id())) return 0.7f;  // Water reduces light more
        if (block.id() == Blocks.LEAVES.id()) return 0.85f;  // Leaves slightly reduce light
        return 1.0f;  // No extra reduction
    }

    private static void addAffectedChunk(Set<ChunkPos> set, int wx, int wy, int wz) {
        int cx = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE);
        set.add(new ChunkPos(cx, cz));
    }

    private static long packPos(int x, int y, int z) {
        return ((long)(x + 30000000) << 36) | ((long)(y & 0xFFF) << 24) | ((long)(z + 30000000) & 0xFFFFFFL);
    }
}
