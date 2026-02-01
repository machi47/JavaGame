package com.voxelgame.sim;

/**
 * Game modes controlling damage scaling, invulnerability, and flight access.
 *
 * CREATIVE  — invulnerable, free flight
 * SURVIVAL  — normal damage (1×), no flight
 * SAFE      — half damage (0.5×), no flight
 * EASY      — normal damage (1×), no flight (same as SURVIVAL)
 * MEDIUM    — increased damage (1.5×), no flight
 * HARD      — double damage (2×), no flight
 */
public enum GameMode {

    CREATIVE(0.0f, true,  true),
    SURVIVAL(1.0f, false, false),
    SAFE    (0.5f, false, false),
    EASY    (1.0f, false, false),
    MEDIUM  (1.5f, false, false),
    HARD    (2.0f, false, false);

    private final float  damageMultiplier;
    private final boolean invulnerable;
    private final boolean flightAllowed;

    GameMode(float damageMultiplier, boolean invulnerable, boolean flightAllowed) {
        this.damageMultiplier = damageMultiplier;
        this.invulnerable     = invulnerable;
        this.flightAllowed    = flightAllowed;
    }

    public float  getDamageMultiplier() { return damageMultiplier; }
    public boolean isInvulnerable()     { return invulnerable; }
    public boolean isFlightAllowed()    { return flightAllowed; }

    /** Cycle to the next game mode (wraps around). */
    public GameMode next() {
        GameMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    /** Safe parse from string — defaults to SURVIVAL if unrecognized. */
    public static GameMode fromString(String s) {
        if (s == null) return SURVIVAL;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SURVIVAL;
        }
    }
}
