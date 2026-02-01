package com.voxelgame.world;

/**
 * In-game time tracking. Manages day/night cycle.
 *
 * One full day = 24000 ticks = 20 real minutes (at 20 ticks/sec).
 *
 * Time of day ranges:
 *   0-1000    sunrise / dawn
 *   1000-12000  daytime
 *   12000-13000 sunset / dusk
 *   13000-23000 nighttime
 *   23000-24000 pre-dawn
 *
 * World starts at tick 6000 (mid-morning).
 */
public class WorldTime {

    // ---- Constants ----
    public static final int DAY_LENGTH       = 24000;
    public static final int TICKS_PER_SECOND = 20;

    // ---- Time ranges ----
    public static final int SUNRISE_START = 0;
    public static final int DAY_START     = 1000;
    public static final int SUNSET_START  = 12000;
    public static final int NIGHT_START   = 13000;
    public static final int DAWN_START    = 23000;

    // ---- State ----
    private long worldTick = 6000; // start at mid-morning
    private float tickAccumulator = 0;

    /**
     * Advance world time based on real elapsed time.
     */
    public void update(float dt) {
        tickAccumulator += dt * TICKS_PER_SECOND;
        int ticks = (int) tickAccumulator;
        if (ticks > 0) {
            worldTick += ticks;
            tickAccumulator -= ticks;
        }
    }

    /** Get the absolute world tick (increases forever). */
    public long getWorldTick() { return worldTick; }

    /** Get time of day (0-23999). */
    public int getTimeOfDay() { return (int) (worldTick % DAY_LENGTH); }

    /** Get current day number (starting from 0). */
    public int getDayNumber() { return (int) (worldTick / DAY_LENGTH); }

    /**
     * Is it currently daytime? (sunrise through sunset)
     */
    public boolean isDay() {
        int tod = getTimeOfDay();
        return tod >= SUNRISE_START && tod < SUNSET_START;
    }

    /**
     * Is it currently nighttime? (sunset through dawn)
     */
    public boolean isNight() {
        int tod = getTimeOfDay();
        return tod >= NIGHT_START || tod < SUNRISE_START;
    }

    /**
     * Get sun brightness factor (0.0 = full night, 1.0 = full day).
     * Smooth transitions during sunrise/sunset.
     */
    public float getSunBrightness() {
        int tod = getTimeOfDay();

        if (tod >= DAY_START && tod < SUNSET_START) {
            // Full daylight
            return 1.0f;
        } else if (tod >= NIGHT_START && tod < DAWN_START) {
            // Full night
            return 0.15f;
        } else if (tod < DAY_START) {
            // Sunrise transition (0-1000)
            float t = tod / (float) DAY_START;
            return 0.15f + 0.85f * t;
        } else if (tod < NIGHT_START) {
            // Sunset transition (12000-13000)
            float t = (tod - SUNSET_START) / (float) (NIGHT_START - SUNSET_START);
            return 1.0f - 0.85f * t;
        } else {
            // Dawn transition (23000-24000)
            float t = (tod - DAWN_START) / (float) (DAY_LENGTH - DAWN_START);
            return 0.15f + 0.85f * t;
        }
    }

    /**
     * Get a human-readable time string (HH:MM format).
     * 0 ticks = 6:00 AM (Minecraft convention).
     */
    public String getTimeString() {
        int tod = getTimeOfDay();
        // Convert ticks to minutes: 24000 ticks = 1440 minutes (24 hours)
        int totalMinutes = (int) (tod / (24000.0 / 1440.0));
        // Offset by 6 hours (Minecraft: tick 0 = 6:00 AM)
        totalMinutes = (totalMinutes + 360) % 1440;
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    /** Set world tick (for save/load). */
    public void setWorldTick(long tick) {
        this.worldTick = tick;
    }
}
