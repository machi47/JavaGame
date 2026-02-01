package com.voxelgame.sim;

/**
 * Game modes controlling HOW THE PLAYER PLAYS — not difficulty.
 * Difficulty (damage scaling) is handled by {@link Difficulty}.
 *
 * CREATIVE  — invulnerable, infinite blocks, instant break, free flight
 * SURVIVAL  — health system, finite blocks, time-based breaking, no flight
 */
public enum GameMode {

    CREATIVE(true, true, true),
    SURVIVAL(false, false, false);

    private final boolean invulnerable;
    private final boolean flightAllowed;
    private final boolean instantBreak;

    GameMode(boolean invulnerable, boolean flightAllowed, boolean instantBreak) {
        this.invulnerable  = invulnerable;
        this.flightAllowed = flightAllowed;
        this.instantBreak  = instantBreak;
    }

    public boolean isInvulnerable()  { return invulnerable; }
    public boolean isFlightAllowed() { return flightAllowed; }
    public boolean isInstantBreak()  { return instantBreak; }

    /** Cycle to the next game mode (Creative ↔ Survival). */
    public GameMode next() {
        GameMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    /**
     * Safe parse from string — defaults to SURVIVAL if unrecognized.
     * Backward compatible: old modes (SAFE, EASY, MEDIUM, HARD) map to SURVIVAL.
     */
    public static GameMode fromString(String s) {
        if (s == null) return SURVIVAL;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Old mode names from Phase 1 all map to SURVIVAL
            return SURVIVAL;
        }
    }
}
