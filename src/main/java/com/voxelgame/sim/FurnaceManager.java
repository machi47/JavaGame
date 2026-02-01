package com.voxelgame.sim;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * Manages all furnace tile entities in the world.
 * Provides lookup by position, creation, removal, tick updating,
 * and save/load persistence.
 */
public class FurnaceManager {

    private final List<Furnace> furnaces = new ArrayList<>();

    /**
     * Create a new furnace at the given position.
     * If one already exists there, returns the existing one.
     */
    public Furnace createFurnace(int x, int y, int z) {
        Furnace existing = getFurnaceAt(x, y, z);
        if (existing != null) return existing;

        Furnace furnace = new Furnace(x, y, z);
        furnaces.add(furnace);
        System.out.printf("[Furnace] Created furnace at (%d, %d, %d)%n", x, y, z);
        return furnace;
    }

    /**
     * Get the furnace at the given position, or null if none.
     */
    public Furnace getFurnaceAt(int x, int y, int z) {
        for (Furnace f : furnaces) {
            if (f.isAt(x, y, z)) return f;
        }
        return null;
    }

    /**
     * Remove the furnace at the given position and return it (for dropping items).
     */
    public Furnace removeFurnace(int x, int y, int z) {
        Iterator<Furnace> iter = furnaces.iterator();
        while (iter.hasNext()) {
            Furnace f = iter.next();
            if (f.isAt(x, y, z)) {
                iter.remove();
                System.out.printf("[Furnace] Removed furnace at (%d, %d, %d)%n", x, y, z);
                return f;
            }
        }
        return null;
    }

    /**
     * Tick all furnaces. Called every game tick from the main loop.
     */
    public void tickAll() {
        for (Furnace f : furnaces) {
            f.tick();
        }
    }

    public List<Furnace> getAllFurnaces() {
        return furnaces;
    }

    public void clear() {
        furnaces.clear();
    }

    public int getFurnaceCount() {
        return furnaces.size();
    }

    // ================================================================
    // Save / Load persistence
    // ================================================================

    /**
     * Save all furnace tile entities to a furnaces.dat file.
     * Format: Properties file with indexed entries.
     */
    public void save(Path saveDir) throws IOException {
        saveDir.toFile().mkdirs();
        Path file = saveDir.resolve("furnaces.dat");

        Properties props = new Properties();
        props.setProperty("count", Integer.toString(furnaces.size()));

        for (int i = 0; i < furnaces.size(); i++) {
            Furnace f = furnaces.get(i);
            String prefix = "f" + i + ".";
            props.setProperty(prefix + "x", Integer.toString(f.getX()));
            props.setProperty(prefix + "y", Integer.toString(f.getY()));
            props.setProperty(prefix + "z", Integer.toString(f.getZ()));
            props.setProperty(prefix + "inputId", Integer.toString(f.getInputId()));
            props.setProperty(prefix + "inputCount", Integer.toString(f.getInputCount()));
            props.setProperty(prefix + "fuelId", Integer.toString(f.getFuelId()));
            props.setProperty(prefix + "fuelCount", Integer.toString(f.getFuelCount()));
            props.setProperty(prefix + "outputId", Integer.toString(f.getOutputId()));
            props.setProperty(prefix + "outputCount", Integer.toString(f.getOutputCount()));
            props.setProperty(prefix + "smeltProgress", Float.toString(f.getSmeltProgress()));
            props.setProperty(prefix + "fuelRemaining", Float.toString(f.getFuelRemaining()));
            props.setProperty(prefix + "currentFuelTotal", Float.toString(f.getCurrentFuelTotal()));
        }

        try (OutputStream os = new FileOutputStream(file.toFile())) {
            props.store(os, "VoxelGame Furnace Tile Entities");
        }

        System.out.println("[FurnaceManager] Saved " + furnaces.size() + " furnaces");
    }

    /**
     * Load furnace tile entities from a furnaces.dat file.
     * Clears existing furnaces and replaces with loaded data.
     */
    public void load(Path saveDir) throws IOException {
        Path file = saveDir.resolve("furnaces.dat");
        if (!file.toFile().exists()) return;

        Properties props = new Properties();
        try (InputStream is = new FileInputStream(file.toFile())) {
            props.load(is);
        }

        furnaces.clear();
        int count = Integer.parseInt(props.getProperty("count", "0"));

        for (int i = 0; i < count; i++) {
            String prefix = "f" + i + ".";
            try {
                int x = Integer.parseInt(props.getProperty(prefix + "x", "0"));
                int y = Integer.parseInt(props.getProperty(prefix + "y", "0"));
                int z = Integer.parseInt(props.getProperty(prefix + "z", "0"));

                Furnace f = new Furnace(x, y, z);
                f.setInput(
                    Integer.parseInt(props.getProperty(prefix + "inputId", "0")),
                    Integer.parseInt(props.getProperty(prefix + "inputCount", "0"))
                );
                f.setFuel(
                    Integer.parseInt(props.getProperty(prefix + "fuelId", "0")),
                    Integer.parseInt(props.getProperty(prefix + "fuelCount", "0"))
                );
                f.setOutput(
                    Integer.parseInt(props.getProperty(prefix + "outputId", "0")),
                    Integer.parseInt(props.getProperty(prefix + "outputCount", "0"))
                );
                f.restoreState(
                    Float.parseFloat(props.getProperty(prefix + "smeltProgress", "0")),
                    Float.parseFloat(props.getProperty(prefix + "fuelRemaining", "0")),
                    Float.parseFloat(props.getProperty(prefix + "currentFuelTotal", "0"))
                );
                furnaces.add(f);
            } catch (NumberFormatException e) {
                System.err.println("[FurnaceManager] Skipped corrupted furnace entry " + i);
            }
        }

        System.out.println("[FurnaceManager] Loaded " + furnaces.size() + " furnaces");
    }
}
