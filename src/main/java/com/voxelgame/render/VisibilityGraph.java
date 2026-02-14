package com.voxelgame.render;

import com.voxelgame.world.*;
import com.voxelgame.world.stream.ChunkManager;

import java.util.*;

/**
 * CPU-based visibility connectivity graph for occlusion culling.
 *
 * Architecture:
 *   Connectivity Graph -> Frustum Cull -> HZB Cull (opaque only) -> Render
 *        |
 *   Hysteresis (sticky visibility to prevent popping)
 *
 * Mental Model:
 * 1. Connectivity: "Can this region be seen in principle?" (coarse, CPU)
 * 2. Frustum: "Is it within the camera cone?"
 * 3. HZB: "Is it hidden behind already-drawn opaque geometry?" (fine, GPU)
 * 4. Hysteresis: "Don't pop visibility at boundaries"
 *
 * Works at the subchunk (16x16x16 section) level for finer granularity.
 */
public class VisibilityGraph {

    /** Budget for BFS nodes per frame to keep CPU cost low. */
    private static final int BFS_BUDGET_PER_FRAME = 100;

    /** Hysteresis: frames to keep a subchunk visible after BFS stops reaching it. */
    private static final int HYSTERESIS_FRAMES = 15;

    /** Grace shell: expand frontier by this many subchunks to prevent harsh cutoffs. */
    private static final int GRACE_SHELL_RADIUS = 2;

    /** Current frame counter for hysteresis. */
    private long currentFrame = 0;

    /** Set of subchunk positions reachable via connectivity this frame. */
    private final Set<SubchunkPos> connectedSet = new HashSet<>();

    /** Last frame each subchunk was visible (for hysteresis). */
    private final Map<SubchunkPos, Long> lastVisibleFrame = new HashMap<>();

    /** BFS frontier for incremental traversal. */
    private final Deque<SubchunkPos> bfsFrontier = new ArrayDeque<>();

    /** Visited set for current BFS wave. */
    private final Set<SubchunkPos> bfsVisited = new HashSet<>();

    /** Cached portal masks per subchunk. Key = subchunk position. */
    private final Map<SubchunkPos, Integer> portalCache = new HashMap<>();

    /** Dirty chunks that need portal recalculation. */
    private final Set<ChunkPos> dirtyChunks = new HashSet<>();

    /** Reference to the world for block lookups. */
    private World world;

    /** Last player subchunk position for BFS restart detection. */
    private SubchunkPos lastPlayerSubchunk;

    /** Debug stats */
    private int lastBfsNodesProcessed = 0;
    private int lastConnectedCount = 0;
    private int lastCandidateChunks = 0;

    /**
     * Position of a 16x16x16 subchunk within the world.
     */
    public record SubchunkPos(int cx, int cy, int cz) {
        /** Get the chunk position containing this subchunk. */
        public ChunkPos toChunkPos() {
            return new ChunkPos(cx, cz);
        }

        /** Get adjacent subchunk in the given direction. */
        public SubchunkPos neighbor(int dx, int dy, int dz) {
            return new SubchunkPos(cx + dx, cy + dy, cz + dz);
        }
    }

    /**
     * Initialize the visibility graph.
     */
    public void init(World world) {
        this.world = world;
    }

    /**
     * Update visibility graph each frame.
     * Performs budgeted BFS from player position.
     *
     * @param playerX Player world X position
     * @param playerY Player world Y position
     * @param playerZ Player world Z position
     */
    public void update(float playerX, float playerY, float playerZ) {
        currentFrame++;

        // Convert player position to subchunk coordinates
        int pcx = (int) Math.floor(playerX / WorldConstants.CHUNK_SIZE);
        int pcy = Math.clamp((int) (playerY / WorldConstants.SECTION_HEIGHT), 0, WorldConstants.SECTIONS_PER_CHUNK - 1);
        int pcz = (int) Math.floor(playerZ / WorldConstants.CHUNK_SIZE);
        SubchunkPos playerSubchunk = new SubchunkPos(pcx, pcy, pcz);

        // Detect if player moved to a new subchunk - restart BFS
        boolean playerMoved = !playerSubchunk.equals(lastPlayerSubchunk);
        if (playerMoved) {
            lastPlayerSubchunk = playerSubchunk;
            restartBfs(playerSubchunk);
        }

        // Run budgeted BFS
        int processed = runBudgetedBfs();
        lastBfsNodesProcessed = processed;
        lastConnectedCount = connectedSet.size();

        // Update hysteresis - mark newly connected subchunks
        for (SubchunkPos pos : connectedSet) {
            lastVisibleFrame.put(pos, currentFrame);
        }

        // Cleanup old hysteresis entries (older than 2x hysteresis window)
        if (currentFrame % 60 == 0) {
            lastVisibleFrame.entrySet().removeIf(e ->
                currentFrame - e.getValue() > HYSTERESIS_FRAMES * 2);
        }
    }

    /**
     * Get the set of candidate chunks that may be visible.
     * Combines connectivity-reachable chunks with hysteresis.
     *
     * @return Set of ChunkPos that should be considered for rendering
     */
    public Set<ChunkPos> getCandidateChunks() {
        Set<ChunkPos> candidates = new HashSet<>();

        // Add all chunks containing connected subchunks
        for (SubchunkPos pos : connectedSet) {
            candidates.add(pos.toChunkPos());
        }

        // Add chunks still within hysteresis window
        for (Map.Entry<SubchunkPos, Long> entry : lastVisibleFrame.entrySet()) {
            if (currentFrame - entry.getValue() < HYSTERESIS_FRAMES) {
                candidates.add(entry.getKey().toChunkPos());
            }
        }

        lastCandidateChunks = candidates.size();
        return candidates;
    }

    /**
     * Check if a specific subchunk is currently considered visible.
     */
    public boolean isSubchunkVisible(int cx, int cy, int cz) {
        SubchunkPos pos = new SubchunkPos(cx, cy, cz);

        // Check direct connectivity
        if (connectedSet.contains(pos)) {
            return true;
        }

        // Check hysteresis
        Long lastSeen = lastVisibleFrame.get(pos);
        return lastSeen != null && (currentFrame - lastSeen) < HYSTERESIS_FRAMES;
    }

    /**
     * Check if a chunk (any section) is considered visible.
     */
    public boolean isChunkVisible(int cx, int cz) {
        for (int cy = 0; cy < WorldConstants.SECTIONS_PER_CHUNK; cy++) {
            if (isSubchunkVisible(cx, cy, cz)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mark a chunk as dirty (needs portal recalculation).
     * Call when blocks change in a chunk.
     */
    public void markChunkDirty(ChunkPos pos) {
        dirtyChunks.add(pos);
        // Invalidate portal cache for all sections in this chunk
        for (int cy = 0; cy < WorldConstants.SECTIONS_PER_CHUNK; cy++) {
            portalCache.remove(new SubchunkPos(pos.x(), cy, pos.z()));
        }
    }

    /**
     * Restart BFS from a new origin subchunk.
     */
    private void restartBfs(SubchunkPos origin) {
        bfsFrontier.clear();
        bfsVisited.clear();
        connectedSet.clear();

        // Start from player subchunk
        bfsFrontier.add(origin);
        bfsVisited.add(origin);
        connectedSet.add(origin);

        // Add grace shell around player
        for (int dx = -GRACE_SHELL_RADIUS; dx <= GRACE_SHELL_RADIUS; dx++) {
            for (int dy = -1; dy <= 1; dy++) { // Vertical grace is smaller
                for (int dz = -GRACE_SHELL_RADIUS; dz <= GRACE_SHELL_RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    int newCy = origin.cy + dy;
                    if (newCy < 0 || newCy >= WorldConstants.SECTIONS_PER_CHUNK) continue;

                    SubchunkPos neighbor = new SubchunkPos(origin.cx + dx, newCy, origin.cz + dz);
                    if (!bfsVisited.contains(neighbor)) {
                        bfsFrontier.add(neighbor);
                        bfsVisited.add(neighbor);
                        connectedSet.add(neighbor);
                    }
                }
            }
        }
    }

    /**
     * Run BFS with a per-frame budget to spread work over multiple frames.
     * @return Number of nodes processed this frame
     */
    private int runBudgetedBfs() {
        int processed = 0;

        while (!bfsFrontier.isEmpty() && processed < BFS_BUDGET_PER_FRAME) {
            SubchunkPos current = bfsFrontier.poll();
            processed++;

            // Get portal mask for this subchunk
            int portalMask = getPortalMask(current);

            // Check each of the 6 faces
            int[][] neighbors = {
                {0, 1, 0, PORTAL_TOP},    // +Y
                {0, -1, 0, PORTAL_BOTTOM}, // -Y
                {0, 0, -1, PORTAL_NORTH},  // -Z
                {0, 0, 1, PORTAL_SOUTH},   // +Z
                {1, 0, 0, PORTAL_EAST},    // +X
                {-1, 0, 0, PORTAL_WEST}    // -X
            };

            for (int[] n : neighbors) {
                int dx = n[0], dy = n[1], dz = n[2];
                int portalBit = n[3];

                // Check if this face has a portal (any non-opaque block)
                if ((portalMask & portalBit) == 0) continue;

                int newCy = current.cy + dy;
                if (newCy < 0 || newCy >= WorldConstants.SECTIONS_PER_CHUNK) continue;

                SubchunkPos neighbor = new SubchunkPos(current.cx + dx, newCy, current.cz + dz);

                // Check if neighbor subchunk has corresponding portal
                int neighborPortalMask = getPortalMask(neighbor);
                int oppositePortal = getOppositePortal(portalBit);
                if ((neighborPortalMask & oppositePortal) == 0) continue;

                // Add to frontier if not visited
                if (!bfsVisited.contains(neighbor)) {
                    bfsVisited.add(neighbor);
                    connectedSet.add(neighbor);
                    bfsFrontier.add(neighbor);
                }
            }
        }

        return processed;
    }

    // Portal mask bits
    private static final int PORTAL_TOP = 1;
    private static final int PORTAL_BOTTOM = 2;
    private static final int PORTAL_NORTH = 4;
    private static final int PORTAL_SOUTH = 8;
    private static final int PORTAL_EAST = 16;
    private static final int PORTAL_WEST = 32;

    /**
     * Get the opposite portal direction.
     */
    private int getOppositePortal(int portal) {
        return switch (portal) {
            case PORTAL_TOP -> PORTAL_BOTTOM;
            case PORTAL_BOTTOM -> PORTAL_TOP;
            case PORTAL_NORTH -> PORTAL_SOUTH;
            case PORTAL_SOUTH -> PORTAL_NORTH;
            case PORTAL_EAST -> PORTAL_WEST;
            case PORTAL_WEST -> PORTAL_EAST;
            default -> 0;
        };
    }

    /**
     * Get or compute portal mask for a subchunk.
     * Each bit represents whether light/visibility can pass through that face.
     */
    private int getPortalMask(SubchunkPos pos) {
        // Check cache first
        Integer cached = portalCache.get(pos);
        if (cached != null) {
            return cached;
        }

        // Compute portal mask
        int mask = computePortalMask(pos);
        portalCache.put(pos, mask);
        return mask;
    }

    /**
     * Compute portal mask by checking each face of the 16x16x16 section.
     * A face has a portal if ANY block on that face is non-opaque.
     */
    private int computePortalMask(SubchunkPos pos) {
        if (world == null) return 0x3F; // All portals open if no world

        Chunk chunk = world.getChunk(pos.cx, pos.cz);
        if (chunk == null) {
            // Chunk not loaded - assume all portals open (conservative)
            return 0x3F;
        }

        int yStart = pos.cy * WorldConstants.SECTION_HEIGHT;
        int yEnd = yStart + WorldConstants.SECTION_HEIGHT;

        int mask = 0;

        // Check top face (Y = yEnd - 1)
        if (yEnd <= WorldConstants.WORLD_HEIGHT) {
            if (faceHasPortal(chunk, 0, WorldConstants.CHUNK_SIZE, yEnd - 1, yEnd, 0, WorldConstants.CHUNK_SIZE, 'Y')) {
                mask |= PORTAL_TOP;
            }
        } else {
            mask |= PORTAL_TOP; // Above world = open to sky
        }

        // Check bottom face (Y = yStart)
        if (yStart >= 0) {
            if (faceHasPortal(chunk, 0, WorldConstants.CHUNK_SIZE, yStart, yStart + 1, 0, WorldConstants.CHUNK_SIZE, 'Y')) {
                mask |= PORTAL_BOTTOM;
            }
        }

        // Check north face (Z = 0)
        if (faceHasPortal(chunk, 0, WorldConstants.CHUNK_SIZE, yStart, yEnd, 0, 1, 'Z')) {
            mask |= PORTAL_NORTH;
        }

        // Check south face (Z = 15)
        if (faceHasPortal(chunk, 0, WorldConstants.CHUNK_SIZE, yStart, yEnd, WorldConstants.CHUNK_SIZE - 1, WorldConstants.CHUNK_SIZE, 'Z')) {
            mask |= PORTAL_SOUTH;
        }

        // Check west face (X = 0)
        if (faceHasPortal(chunk, 0, 1, yStart, yEnd, 0, WorldConstants.CHUNK_SIZE, 'X')) {
            mask |= PORTAL_WEST;
        }

        // Check east face (X = 15)
        if (faceHasPortal(chunk, WorldConstants.CHUNK_SIZE - 1, WorldConstants.CHUNK_SIZE, yStart, yEnd, 0, WorldConstants.CHUNK_SIZE, 'X')) {
            mask |= PORTAL_EAST;
        }

        return mask;
    }

    /**
     * Check if a face of the section has any non-opaque blocks (portal).
     */
    private boolean faceHasPortal(Chunk chunk, int x0, int x1, int y0, int y1, int z0, int z1, char axis) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    int blockId = chunk.getBlock(x, y, z);
                    if (!isOpaqueForOcclusion(blockId)) {
                        return true; // Found a portal (non-opaque block)
                    }
                }
            }
        }
        return false; // Face is fully opaque
    }

    /**
     * Check if a block is opaque for occlusion purposes.
     * Opaque = solid AND not transparent.
     * Air, water, glass, leaves all count as non-opaque (open for visibility).
     */
    private boolean isOpaqueForOcclusion(int blockId) {
        if (blockId == 0) return false; // Air
        Block block = Blocks.get(blockId);
        return block.solid() && !block.transparent();
    }

    // --- Debug accessors ---

    public int getLastBfsNodesProcessed() { return lastBfsNodesProcessed; }
    public int getLastConnectedCount() { return lastConnectedCount; }
    public int getLastCandidateChunks() { return lastCandidateChunks; }
    public int getPortalCacheSize() { return portalCache.size(); }
    public int getHysteresisMapSize() { return lastVisibleFrame.size(); }

    /**
     * Clear all cached data (call on world unload).
     */
    public void clear() {
        connectedSet.clear();
        lastVisibleFrame.clear();
        bfsFrontier.clear();
        bfsVisited.clear();
        portalCache.clear();
        dirtyChunks.clear();
        lastPlayerSubchunk = null;
    }
}
