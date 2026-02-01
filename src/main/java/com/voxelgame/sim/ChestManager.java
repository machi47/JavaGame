package com.voxelgame.sim;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * Manages all chest tile entities in the world.
 * Provides lookup by position, creation, removal, and save/load persistence.
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

    // ================================================================
    // Save / Load persistence
    // ================================================================

    /**
     * Save all chest tile entities to a chests.dat file.
     */
    public void save(Path saveDir) throws IOException {
        saveDir.toFile().mkdirs();
        Path file = saveDir.resolve("chests.dat");

        Properties props = new Properties();
        props.setProperty("count", Integer.toString(chests.size()));

        for (int i = 0; i < chests.size(); i++) {
            Chest c = chests.get(i);
            String prefix = "c" + i + ".";
            props.setProperty(prefix + "x", Integer.toString(c.getX()));
            props.setProperty(prefix + "y", Integer.toString(c.getY()));
            props.setProperty(prefix + "z", Integer.toString(c.getZ()));

            int[] data = c.serialize();
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < data.length; j++) {
                if (j > 0) sb.append(",");
                sb.append(data[j]);
            }
            props.setProperty(prefix + "inventory", sb.toString());
        }

        try (OutputStream os = new FileOutputStream(file.toFile())) {
            props.store(os, "VoxelGame Chest Tile Entities");
        }

        System.out.println("[ChestManager] Saved " + chests.size() + " chests");
    }

    /**
     * Load chest tile entities from a chests.dat file.
     */
    public void load(Path saveDir) throws IOException {
        Path file = saveDir.resolve("chests.dat");
        if (!file.toFile().exists()) return;

        Properties props = new Properties();
        try (InputStream is = new FileInputStream(file.toFile())) {
            props.load(is);
        }

        chests.clear();
        int count = Integer.parseInt(props.getProperty("count", "0"));

        for (int i = 0; i < count; i++) {
            String prefix = "c" + i + ".";
            try {
                int x = Integer.parseInt(props.getProperty(prefix + "x", "0"));
                int y = Integer.parseInt(props.getProperty(prefix + "y", "0"));
                int z = Integer.parseInt(props.getProperty(prefix + "z", "0"));

                Chest c = new Chest(x, y, z);

                String invStr = props.getProperty(prefix + "inventory", "");
                if (!invStr.isEmpty()) {
                    String[] parts = invStr.split(",");
                    int[] data = new int[parts.length];
                    for (int j = 0; j < parts.length; j++) {
                        data[j] = Integer.parseInt(parts[j].trim());
                    }
                    c.deserialize(data);
                }

                chests.add(c);
            } catch (NumberFormatException e) {
                System.err.println("[ChestManager] Skipped corrupted chest entry " + i);
            }
        }

        System.out.println("[ChestManager] Loaded " + chests.size() + " chests");
    }
}
