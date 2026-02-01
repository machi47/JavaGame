package com.voxelgame.sim;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages all chest tile entities in the world.
 * Provides lookup by position, creation, and removal.
 */
public class ChestManager {

    private final List<Chest> chests = new ArrayList<>();

    /**
     * Create a new chest at the given position.
     * If one already exists there, returns the existing one.
     */
    public Chest createChest(int x, int y, int z) {
        Chest existing = getChestAt(x, y, z);
        if (existing != null) return existing;

        Chest chest = new Chest(x, y, z);
        chests.add(chest);
        System.out.printf("[Chest] Created chest at (%d, %d, %d)%n", x, y, z);
        return chest;
    }

    /**
     * Get the chest at the given position, or null if none.
     */
    public Chest getChestAt(int x, int y, int z) {
        for (Chest chest : chests) {
            if (chest.isAt(x, y, z)) return chest;
        }
        return null;
    }

    /**
     * Remove the chest at the given position and return it (for dropping items).
     * Returns null if no chest exists there.
     */
    public Chest removeChest(int x, int y, int z) {
        Iterator<Chest> iter = chests.iterator();
        while (iter.hasNext()) {
            Chest chest = iter.next();
            if (chest.isAt(x, y, z)) {
                iter.remove();
                System.out.printf("[Chest] Removed chest at (%d, %d, %d)%n", x, y, z);
                return chest;
            }
        }
        return null;
    }

    /**
     * Get all chests (for save/load).
     */
    public List<Chest> getAllChests() {
        return chests;
    }

    /**
     * Clear all chests (e.g., on world unload).
     */
    public void clear() {
        chests.clear();
    }

    /**
     * Get the number of active chests.
     */
    public int getChestCount() {
        return chests.size();
    }
}
