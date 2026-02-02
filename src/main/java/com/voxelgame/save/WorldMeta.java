package com.voxelgame.save;

import com.voxelgame.sim.Difficulty;
import com.voxelgame.sim.GameMode;

import java.io.*;
import java.nio.file.Path;
import java.util.Properties;

/**
 * World metadata stored in world.dat as a simple key=value properties file.
 * Contains: seed, player position/rotation, game mode, spawn point,
 * health, game time, creation time.
 */
public class WorldMeta {

    private long seed;
    private String worldName = "World";
    private float playerX, playerY, playerZ;
    private float playerYaw, playerPitch;
    private long createdAt;
    private long lastPlayedAt;

    // New: game mode, difficulty, spawn point, health
    private GameMode gameMode = GameMode.SURVIVAL;
    private Difficulty difficulty = Difficulty.NORMAL;
    private float spawnX, spawnY, spawnZ;
    private float playerHealth = 20.0f;

    public WorldMeta() {
        this.createdAt = System.currentTimeMillis();
        this.lastPlayedAt = this.createdAt;
    }

    public WorldMeta(long seed) {
        this();
        this.seed = seed;
    }

    // --- Getters / Setters ---

    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }

    public String getWorldName() { return worldName; }
    public void setWorldName(String name) { this.worldName = name != null ? name : "World"; }

    public float getPlayerX() { return playerX; }
    public float getPlayerY() { return playerY; }
    public float getPlayerZ() { return playerZ; }
    public void setPlayerPosition(float x, float y, float z) {
        this.playerX = x;
        this.playerY = y;
        this.playerZ = z;
    }

    public float getPlayerYaw() { return playerYaw; }
    public float getPlayerPitch() { return playerPitch; }
    public void setPlayerRotation(float yaw, float pitch) {
        this.playerYaw = yaw;
        this.playerPitch = pitch;
    }

    public long getCreatedAt() { return createdAt; }
    public long getLastPlayedAt() { return lastPlayedAt; }
    public void setLastPlayedAt(long time) { this.lastPlayedAt = time; }

    // --- Game mode ---

    public GameMode getGameMode() { return gameMode; }
    public void setGameMode(GameMode mode) { this.gameMode = mode; }

    // --- Difficulty ---

    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty diff) { this.difficulty = diff; }

    // --- Spawn point ---

    public float getSpawnX() { return spawnX; }
    public float getSpawnY() { return spawnY; }
    public float getSpawnZ() { return spawnZ; }
    public void setSpawnPoint(float x, float y, float z) {
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
    }

    // --- Player health ---

    public float getPlayerHealth() { return playerHealth; }
    public void setPlayerHealth(float health) { this.playerHealth = health; }

    // --- World generation preset ---

    private String worldGenPreset = "DEFAULT";
    private String genConfigData = "";  // serialized advanced settings

    public String getWorldGenPreset() { return worldGenPreset; }
    public void setWorldGenPreset(String preset) { this.worldGenPreset = preset != null ? preset : "DEFAULT"; }

    public String getGenConfigData() { return genConfigData; }
    public void setGenConfigData(String data) { this.genConfigData = data != null ? data : ""; }

    // --- Player inventory ---

    private String inventoryData = "";

    public String getInventoryData() { return inventoryData; }
    public void setInventoryData(String data) { this.inventoryData = data != null ? data : ""; }

    // --- Serialization ---

    /**
     * Save metadata to a world.dat file in the given directory.
     */
    public void save(Path directory) throws IOException {
        directory.toFile().mkdirs();
        Path file = directory.resolve("world.dat");

        Properties props = new Properties();
        props.setProperty("seed", Long.toString(seed));
        props.setProperty("worldName", worldName);
        props.setProperty("playerX", Float.toString(playerX));
        props.setProperty("playerY", Float.toString(playerY));
        props.setProperty("playerZ", Float.toString(playerZ));
        props.setProperty("playerYaw", Float.toString(playerYaw));
        props.setProperty("playerPitch", Float.toString(playerPitch));
        props.setProperty("createdAt", Long.toString(createdAt));
        props.setProperty("lastPlayedAt", Long.toString(System.currentTimeMillis()));

        // New fields
        props.setProperty("gameMode", gameMode.name());
        props.setProperty("difficulty", difficulty.name());
        props.setProperty("spawnX", Float.toString(spawnX));
        props.setProperty("spawnY", Float.toString(spawnY));
        props.setProperty("spawnZ", Float.toString(spawnZ));
        props.setProperty("playerHealth", Float.toString(playerHealth));
        if (inventoryData != null && !inventoryData.isEmpty()) {
            props.setProperty("inventoryData", inventoryData);
        }
        props.setProperty("worldGenPreset", worldGenPreset);
        if (genConfigData != null && !genConfigData.isEmpty()) {
            props.setProperty("genConfigData", genConfigData);
        }

        try (OutputStream os = new FileOutputStream(file.toFile())) {
            props.store(os, "VoxelGame World Metadata");
        }
    }

    /**
     * Load metadata from a world.dat file in the given directory.
     * Returns null if the file doesn't exist.
     */
    public static WorldMeta load(Path directory) throws IOException {
        Path file = directory.resolve("world.dat");
        if (!file.toFile().exists()) return null;

        Properties props = new Properties();
        try (InputStream is = new FileInputStream(file.toFile())) {
            props.load(is);
        }

        WorldMeta meta = new WorldMeta();
        meta.seed = Long.parseLong(props.getProperty("seed", "0"));
        meta.worldName = props.getProperty("worldName", "World");
        meta.playerX = Float.parseFloat(props.getProperty("playerX", "0"));
        meta.playerY = Float.parseFloat(props.getProperty("playerY", "80"));
        meta.playerZ = Float.parseFloat(props.getProperty("playerZ", "0"));
        meta.playerYaw = Float.parseFloat(props.getProperty("playerYaw", "0"));
        meta.playerPitch = Float.parseFloat(props.getProperty("playerPitch", "0"));
        long now = System.currentTimeMillis();
        meta.createdAt = Long.parseLong(props.getProperty("createdAt", Long.toString(now)));
        meta.lastPlayedAt = Long.parseLong(props.getProperty("lastPlayedAt", Long.toString(now)));
        if (meta.createdAt == 0) meta.createdAt = now;
        if (meta.lastPlayedAt == 0) meta.lastPlayedAt = now;

        // New fields (with backwards-compatible defaults)
        meta.gameMode = GameMode.fromString(props.getProperty("gameMode", "SURVIVAL"));
        meta.difficulty = Difficulty.fromString(props.getProperty("difficulty", "NORMAL"));
        meta.spawnX = Float.parseFloat(props.getProperty("spawnX", props.getProperty("playerX", "0")));
        meta.spawnY = Float.parseFloat(props.getProperty("spawnY", props.getProperty("playerY", "80")));
        meta.spawnZ = Float.parseFloat(props.getProperty("spawnZ", props.getProperty("playerZ", "0")));
        meta.playerHealth = Float.parseFloat(props.getProperty("playerHealth", "20.0"));
        meta.inventoryData = props.getProperty("inventoryData", "");
        meta.worldGenPreset = props.getProperty("worldGenPreset", "DEFAULT");
        meta.genConfigData = props.getProperty("genConfigData", "");

        return meta;
    }

    /**
     * Check if a world.dat file exists in the given directory.
     */
    public static boolean exists(Path directory) {
        return directory.resolve("world.dat").toFile().exists();
    }
}
