package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;

import java.util.*;

/**
 * Redstone power propagation system.
 *
 * Power sources: redstone torch (level 15), repeater output (level 15).
 * Wire: redstone dust propagates power with -1 per block (max range 15).
 * Repeaters: receive power on input side, output level 15 after delay.
 * Powered blocks: solid blocks adjacent to power sources become weakly powered.
 *
 * Power levels: 0 = unpowered, 1-15 = powered (15 = strongest).
 *
 * This system uses a BFS approach for propagation and a removal+re-propagation
 * approach for updates (similar to how lighting works).
 */
public class RedstoneSystem {

    /** 4 horizontal directions: +X, -X, +Z, -Z */
    private static final int[][] H_DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    /** 6 cardinal directions */
    private static final int[][] ALL_DIRS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    /**
     * Per-block power level storage. Uses world coordinates packed into a long key.
     * Only stores non-zero power levels.
     */
    private final Map<Long, Integer> powerLevels = new HashMap<>();

    /**
     * Get the power level at a position.
     */
    public int getPowerLevel(int x, int y, int z) {
        return powerLevels.getOrDefault(packPos(x, y, z), 0);
    }

    /**
     * Set the power level at a position.
     */
    private void setPowerLevel(int x, int y, int z, int level) {
        long key = packPos(x, y, z);
        if (level <= 0) {
            powerLevels.remove(key);
        } else {
            powerLevels.put(key, level);
        }
    }

    /**
     * Check if a position is powered (power level > 0).
     */
    public boolean isPowered(int x, int y, int z) {
        return getPowerLevel(x, y, z) > 0;
    }

    /**
     * Check if a block position is receiving redstone power from any adjacent source.
     * This checks if any neighbor is a power source or powered wire/repeater.
     * Used for powered rails and other redstone-activated blocks.
     */
    public boolean isBlockPowered(World world, int x, int y, int z) {
        // Check all 6 neighbors for power
        for (int[] dir : ALL_DIRS) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            int nz = z + dir[2];
            if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

            int blockId = world.getBlock(nx, ny, nz);

            // Redstone torch always powers adjacent blocks
            if (blockId == Blocks.REDSTONE_TORCH.id()) return true;

            // Redstone wire powers if it has power level > 0
            if (blockId == Blocks.REDSTONE_WIRE.id() && getPowerLevel(nx, ny, nz) > 0) return true;

            // Repeater powers the block it faces (simplified: powers all adjacent)
            if (blockId == Blocks.REDSTONE_REPEATER.id() && getPowerLevel(nx, ny, nz) > 0) return true;
        }

        // Also check wire/power below (for rails on top of powered wire)
        int belowId = world.getBlock(x, y - 1, z);
        if (belowId == Blocks.REDSTONE_WIRE.id() && getPowerLevel(x, y - 1, z) > 0) return true;
        if (belowId == Blocks.REDSTONE_TORCH.id()) return true;

        return false;
    }

    /**
     * Called when a redstone component is placed. Propagates power from this position.
     * Returns the set of affected chunk positions for mesh rebuilding.
     */
    public Set<ChunkPos> onRedstonePlaced(World world, int x, int y, int z) {
        Set<ChunkPos> affected = new HashSet<>();
        int blockId = world.getBlock(x, y, z);

        if (blockId == Blocks.REDSTONE_TORCH.id()) {
            // Torch is a power source at level 15
            setPowerLevel(x, y, z, 15);
            addAffected(affected, x, y, z);
            propagateFromSource(world, x, y, z, 15, affected);
        } else if (blockId == Blocks.REDSTONE_WIRE.id()) {
            // Wire receives power from neighbors, then propagates
            int maxNeighborPower = getMaxNeighborWirePower(world, x, y, z);
            if (maxNeighborPower > 1) {
                setPowerLevel(x, y, z, maxNeighborPower - 1);
                addAffected(affected, x, y, z);
                propagateWire(world, x, y, z, maxNeighborPower - 1, affected);
            }
        } else if (blockId == Blocks.REDSTONE_REPEATER.id()) {
            // Repeater: check if input side is powered, then output level 15
            int inputPower = getMaxNeighborWirePower(world, x, y, z);
            if (inputPower > 0) {
                setPowerLevel(x, y, z, 15);
                addAffected(affected, x, y, z);
                propagateFromSource(world, x, y, z, 15, affected);
            }
        }

        return affected;
    }

    /**
     * Called when a redstone component is removed. Removes power and re-propagates.
     * Returns affected chunk positions.
     */
    public Set<ChunkPos> onRedstoneRemoved(World world, int x, int y, int z, int oldBlockId) {
        Set<ChunkPos> affected = new HashSet<>();

        int oldPower = getPowerLevel(x, y, z);
        setPowerLevel(x, y, z, 0);
        addAffected(affected, x, y, z);

        if (oldPower > 0) {
            // Remove all power that was propagated from this position
            removeAndRepropagate(world, x, y, z, oldPower, affected);
        }

        return affected;
    }

    /**
     * Called when any block is placed or removed that might affect redstone.
     * Re-evaluates power propagation around the position.
     */
    public Set<ChunkPos> onBlockChanged(World world, int x, int y, int z) {
        Set<ChunkPos> affected = new HashSet<>();

        // Check all neighbors for redstone components that need updating
        for (int[] dir : ALL_DIRS) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            int nz = z + dir[2];
            if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

            int nBlockId = world.getBlock(nx, ny, nz);
            if (Blocks.isRedstoneComponent(nBlockId)) {
                // Recalculate power for this neighbor
                recalculate(world, nx, ny, nz, affected);
            }
        }

        return affected;
    }

    /**
     * Full recalculation of power at a position based on current neighbors.
     */
    private void recalculate(World world, int x, int y, int z, Set<ChunkPos> affected) {
        int blockId = world.getBlock(x, y, z);
        int oldPower = getPowerLevel(x, y, z);

        int newPower = 0;
        if (blockId == Blocks.REDSTONE_TORCH.id()) {
            // Torch on a powered block is turned off (inverse behavior)
            // Simplified: always on for now
            newPower = 15;
        } else if (blockId == Blocks.REDSTONE_WIRE.id()) {
            newPower = getMaxNeighborWirePower(world, x, y, z);
            if (newPower > 0) newPower -= 1;
        } else if (blockId == Blocks.REDSTONE_REPEATER.id()) {
            int inputPower = getMaxNeighborWirePower(world, x, y, z);
            newPower = inputPower > 0 ? 15 : 0;
        }

        if (newPower != oldPower) {
            setPowerLevel(x, y, z, newPower);
            addAffected(affected, x, y, z);

            if (newPower > oldPower) {
                // Power increased — propagate outward
                if (blockId == Blocks.REDSTONE_WIRE.id()) {
                    propagateWire(world, x, y, z, newPower, affected);
                } else {
                    propagateFromSource(world, x, y, z, newPower, affected);
                }
            } else {
                // Power decreased — need full removal and re-propagation
                removeAndRepropagate(world, x, y, z, oldPower, affected);
            }
        }
    }

    /**
     * Get the maximum power level from neighboring redstone components.
     * Used to determine incoming power for wire and repeaters.
     */
    private int getMaxNeighborWirePower(World world, int x, int y, int z) {
        int maxPower = 0;

        // Check horizontal neighbors
        for (int[] dir : H_DIRS) {
            int nx = x + dir[0];
            int nz = z + dir[2];
            int ny = y;

            int nBlockId = world.getBlock(nx, ny, nz);

            if (nBlockId == Blocks.REDSTONE_TORCH.id()) {
                maxPower = Math.max(maxPower, 15);
            } else if (nBlockId == Blocks.REDSTONE_WIRE.id()) {
                maxPower = Math.max(maxPower, getPowerLevel(nx, ny, nz));
            } else if (nBlockId == Blocks.REDSTONE_REPEATER.id()) {
                maxPower = Math.max(maxPower, getPowerLevel(nx, ny, nz));
            }

            // Wire can go up/down slopes
            if (Blocks.get(nBlockId).solid()) {
                // Check wire on top of adjacent solid block
                int upBlockId = world.getBlock(nx, ny + 1, nz);
                if (upBlockId == Blocks.REDSTONE_WIRE.id()) {
                    maxPower = Math.max(maxPower, getPowerLevel(nx, ny + 1, nz));
                }
            } else {
                // Check wire below in the adjacent column
                int downBlockId = world.getBlock(nx, ny - 1, nz);
                if (downBlockId == Blocks.REDSTONE_WIRE.id()) {
                    maxPower = Math.max(maxPower, getPowerLevel(nx, ny - 1, nz));
                }
            }
        }

        // Check directly above and below
        int aboveId = world.getBlock(x, y + 1, z);
        if (aboveId == Blocks.REDSTONE_TORCH.id()) {
            maxPower = Math.max(maxPower, 15);
        }
        int belowId = world.getBlock(x, y - 1, z);
        if (belowId == Blocks.REDSTONE_TORCH.id()) {
            maxPower = Math.max(maxPower, 15);
        }

        return maxPower;
    }

    /**
     * Propagate power outward from a power source (torch or repeater output).
     */
    private void propagateFromSource(World world, int sx, int sy, int sz, int level, Set<ChunkPos> affected) {
        Queue<int[]> queue = new ArrayDeque<>();

        // Seed neighboring wire
        for (int[] dir : ALL_DIRS) {
            int nx = sx + dir[0];
            int ny = sy + dir[1];
            int nz = sz + dir[2];
            if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) continue;

            int nBlockId = world.getBlock(nx, ny, nz);
            if (nBlockId == Blocks.REDSTONE_WIRE.id()) {
                int newLevel = level - 1;
                if (newLevel > getPowerLevel(nx, ny, nz)) {
                    setPowerLevel(nx, ny, nz, newLevel);
                    addAffected(affected, nx, ny, nz);
                    queue.add(new int[]{nx, ny, nz, newLevel});
                }
            } else if (nBlockId == Blocks.REDSTONE_REPEATER.id()) {
                int inputPower = getMaxNeighborWirePower(world, nx, ny, nz);
                if (inputPower > 0) {
                    setPowerLevel(nx, ny, nz, 15);
                    addAffected(affected, nx, ny, nz);
                    // Repeater re-propagates at full strength
                    queue.add(new int[]{nx, ny, nz, 15});
                }
            }
        }

        // BFS for wire propagation
        propagateWireBFS(world, queue, affected);
    }

    /**
     * Propagate wire power using BFS.
     */
    private void propagateWire(World world, int sx, int sy, int sz, int level, Set<ChunkPos> affected) {
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sx, sy, sz, level});
        propagateWireBFS(world, queue, affected);
    }

    /**
     * BFS wire propagation.
     */
    private void propagateWireBFS(World world, Queue<int[]> queue, Set<ChunkPos> affected) {
        while (!queue.isEmpty()) {
            int[] entry = queue.poll();
            int x = entry[0], y = entry[1], z = entry[2], level = entry[3];

            if (level <= 0) continue;

            // Propagate to horizontal neighbors
            for (int[] dir : H_DIRS) {
                int nx = x + dir[0];
                int nz = z + dir[2];

                // Same level
                checkAndPropagateWire(world, queue, affected, nx, y, nz, level);

                // Wire going up slopes (if adjacent block is solid, check on top)
                int adjId = world.getBlock(nx, y, nz);
                if (Blocks.get(adjId).solid()) {
                    checkAndPropagateWire(world, queue, affected, nx, y + 1, nz, level);
                } else {
                    // Wire going down (if adjacent is air, check below)
                    checkAndPropagateWire(world, queue, affected, nx, y - 1, nz, level);
                }
            }
        }
    }

    private void checkAndPropagateWire(World world, Queue<int[]> queue, Set<ChunkPos> affected,
                                        int nx, int ny, int nz, int sourceLevel) {
        if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) return;

        int nBlockId = world.getBlock(nx, ny, nz);
        if (nBlockId == Blocks.REDSTONE_WIRE.id()) {
            int newLevel = sourceLevel - 1;
            if (newLevel > getPowerLevel(nx, ny, nz)) {
                setPowerLevel(nx, ny, nz, newLevel);
                addAffected(affected, nx, ny, nz);
                queue.add(new int[]{nx, ny, nz, newLevel});
            }
        } else if (nBlockId == Blocks.REDSTONE_REPEATER.id()) {
            int inputPower = getMaxNeighborWirePower(world, nx, ny, nz);
            if (inputPower > 0 && getPowerLevel(nx, ny, nz) == 0) {
                setPowerLevel(nx, ny, nz, 15);
                addAffected(affected, nx, ny, nz);
                // Repeater boosts signal back to 15
                queue.add(new int[]{nx, ny, nz, 15});
            }
        }
    }

    /**
     * Remove power from a position and re-propagate from remaining sources.
     */
    private void removeAndRepropagate(World world, int x, int y, int z, int oldLevel, Set<ChunkPos> affected) {
        Queue<int[]> removeQueue = new ArrayDeque<>();
        Queue<int[]> reproQueue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();

        removeQueue.add(new int[]{x, y, z, oldLevel});

        while (!removeQueue.isEmpty()) {
            int[] entry = removeQueue.poll();
            int ex = entry[0], ey = entry[1], ez = entry[2], level = entry[3];

            for (int[] dir : H_DIRS) {
                int nx = ex + dir[0];
                int nz = ez + dir[2];

                checkRemoveWire(world, removeQueue, reproQueue, visited, affected, nx, ey, nz, level);

                // Slope up/down
                int adjId = world.getBlock(nx, ey, nz);
                if (Blocks.get(adjId).solid()) {
                    checkRemoveWire(world, removeQueue, reproQueue, visited, affected, nx, ey + 1, nz, level);
                } else {
                    checkRemoveWire(world, removeQueue, reproQueue, visited, affected, nx, ey - 1, nz, level);
                }
            }
        }

        // Re-propagate from boundary sources
        propagateWireBFS(world, reproQueue, affected);
    }

    private void checkRemoveWire(World world, Queue<int[]> removeQueue, Queue<int[]> reproQueue,
                                  Set<Long> visited, Set<ChunkPos> affected,
                                  int nx, int ny, int nz, int sourceLevel) {
        if (ny < 0 || ny >= WorldConstants.WORLD_HEIGHT) return;
        long key = packPos(nx, ny, nz);
        if (visited.contains(key)) return;

        int nBlockId = world.getBlock(nx, ny, nz);
        if (nBlockId != Blocks.REDSTONE_WIRE.id() && nBlockId != Blocks.REDSTONE_REPEATER.id()) return;

        int nPower = getPowerLevel(nx, ny, nz);
        if (nPower > 0 && nPower < sourceLevel) {
            // This was powered by the removed source — clear it
            setPowerLevel(nx, ny, nz, 0);
            addAffected(affected, nx, ny, nz);
            visited.add(key);
            removeQueue.add(new int[]{nx, ny, nz, nPower});
        } else if (nPower >= sourceLevel && nPower > 0) {
            // Powered by another source — re-propagate from here
            reproQueue.add(new int[]{nx, ny, nz, nPower});
        }
    }

    /**
     * Clear all stored power data. Called when unloading a world.
     */
    public void clear() {
        powerLevels.clear();
    }

    // ---- Helpers ----

    private static long packPos(int x, int y, int z) {
        return ((long)(x + 30000000) << 36) | ((long)(y & 0xFFF) << 24) | ((long)(z + 30000000) & 0xFFFFFFL);
    }

    private static void addAffected(Set<ChunkPos> set, int wx, int wy, int wz) {
        int cx = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE);
        set.add(new ChunkPos(cx, cz));
    }
}
