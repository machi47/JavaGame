package com.voxelgame.world;

/**
 * In-game time tracking. Manages day/night cycle.
 *
 * One full day = 24000 ticks.
 * Total cycle = 17 real minutes (10 min day + 7 min night) = 1020 seconds.
 * Tick rate = 24000 / 1020 ≈ 23.53 ticks/sec.
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

    /** Real-world seconds for one full day/night cycle (10 min day + 7 min night). */
    public static final float CYCLE_SECONDS  = 1020.0f; // 17 minutes

    /** Tick rate derived from cycle length. */
    public static final float TICKS_PER_SECOND = DAY_LENGTH / CYCLE_SECONDS; // ~23.53

    // ---- Time ranges ----
    public static final int SUNRISE_START = 0;
    public static final int DAY_START     = 1000;
    public static final int SUNSET_START  = 12000;
    public static final int NIGHT_START   = 13000;
    public static final int DAWN_START    = 23000;

    // ---- Brightness tuning ----
    /** Peak sun brightness at midday. 1.0 was overexposed; 0.65 gives bright but natural daylight. */
    private static final float PEAK_SUN_BRIGHTNESS = 0.65f;
    /** Minimum brightness at full night (moonlight). Keeps night very dark; torches essential. */
    private static final float NIGHT_BRIGHTNESS = 0.05f;
    /** Brightness range for transition interpolation. */
    private static final float BRIGHTNESS_RANGE = PEAK_SUN_BRIGHTNESS - NIGHT_BRIGHTNESS;

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
     * Get sun angle in degrees (0 = horizon east, 90 = overhead, 180 = horizon west).
     * Used for celestial rendering.
     */
    public float getSunAngle() {
        int tod = getTimeOfDay();
        // Map day portion (0-12000) to 0-180 degrees
        // Map night portion (12000-24000) to 180-360 degrees
        return (tod / (float) DAY_LENGTH) * 360.0f;
    }

    /**
     * Get sun brightness factor (0.0 = full night, 1.0 = full day).
     * Smooth transitions during sunrise/sunset.
     *
     * Night is DARK. Moon provides only minimal light (level 4 out of 15 ≈ 0.07).
     * This makes torches essential at night.
     */
    public float getSunBrightness() {
        int tod = getTimeOfDay();

        if (tod >= DAY_START && tod < SUNSET_START) {
            // Full daylight — capped below 1.0 to avoid overexposed/washed-out look
            return PEAK_SUN_BRIGHTNESS;
        } else if (tod >= NIGHT_START && tod < DAWN_START) {
            // Full night — moonlight only (very dim)
            // At 0.05, a sky-lit surface gets 0.05 brightness = very dark.
            return NIGHT_BRIGHTNESS;
        } else if (tod < DAY_START) {
            // Sunrise transition (0-1000)
            float t = tod / (float) DAY_START;
            t = smoothstep(t);
            return NIGHT_BRIGHTNESS + BRIGHTNESS_RANGE * t;
        } else if (tod < NIGHT_START) {
            // Sunset transition (12000-13000)
            float t = (tod - SUNSET_START) / (float) (NIGHT_START - SUNSET_START);
            t = smoothstep(t);
            return PEAK_SUN_BRIGHTNESS - BRIGHTNESS_RANGE * t;
        } else {
            // Dawn transition (23000-24000)
            float t = (tod - DAWN_START) / (float) (DAY_LENGTH - DAWN_START);
            t = smoothstep(t);
            return NIGHT_BRIGHTNESS + BRIGHTNESS_RANGE * t;
        }
    }

    /**
     * Get sky color multiplier for fog/clear color.
     * Returns RGB multipliers for the sky.
     * 
     * Enhanced with atmospheric sunset/sunrise colors (orange, pink, purple).
     */
    public float[] getSkyColor() {
        int tod = getTimeOfDay();
        
        // Full day (1000-12000): bright blue sky
        if (tod >= DAY_START && tod < SUNSET_START) {
            return new float[]{0.53f, 0.68f, 0.90f};
        }
        
        // Full night (13000-23000): dark blue-black
        if (tod >= NIGHT_START && tod < DAWN_START) {
            return new float[]{0.01f, 0.01f, 0.03f};
        }
        
        // Sunrise (0-1000): orange → blue
        if (tod < DAY_START) {
            float t = smoothstep(tod / (float) DAY_START);
            return new float[]{
                lerp(0.95f, 0.53f, t), // orange → blue
                lerp(0.55f, 0.68f, t), // warm → cool
                lerp(0.25f, 0.90f, t)  // dark → bright
            };
        }
        
        // Sunset (12000-13000): blue → orange/pink
        if (tod < NIGHT_START) {
            float t = smoothstep((tod - SUNSET_START) / (float) (NIGHT_START - SUNSET_START));
            return new float[]{
                lerp(0.53f, 0.92f, t), // blue → vibrant orange
                lerp(0.68f, 0.50f, t), // cool → warm
                lerp(0.90f, 0.30f, t)  // bright → dusky
            };
        }
        
        // Dawn (23000-24000): dark → orange
        float t = smoothstep((tod - DAWN_START) / (float) (DAY_LENGTH - DAWN_START));
        return new float[]{
            lerp(0.01f, 0.85f, t),
            lerp(0.01f, 0.45f, t),
            lerp(0.03f, 0.25f, t)
        };
    }
    
    /**
     * Get sun direction vector (normalized).
     * Points from world origin toward sun position in sky.
     * 
     * Y component: 0.0 at horizon, 1.0 at zenith (overhead)
     * X/Z components: rotate around Y axis as day progresses
     * 
     * Returns {x, y, z} where y > 0 means sun is above horizon.
     */
    public float[] getSunDirection() {
        int tod = getTimeOfDay();
        
        // Map time of day to angle (0-360 degrees)
        // 0 = sunrise (east), 6000 = noon (zenith), 12000 = sunset (west)
        float angle = (tod / (float) DAY_LENGTH) * 360.0f;
        float radians = (float) Math.toRadians(angle - 90); // Offset so 0 = east
        
        // Sun rises in east, peaks at noon, sets in west
        // Y component: sin wave (0 → 1 → 0 during day, negative at night)
        float y = (float) Math.sin(Math.toRadians(angle));
        
        // X/Z: circle around player (sun moves across sky)
        float horizontalDist = (float) Math.cos(Math.toRadians(angle));
        float x = (float) Math.cos(radians) * Math.abs(horizontalDist);
        float z = (float) Math.sin(radians) * Math.abs(horizontalDist);
        
        // Normalize
        float len = (float) Math.sqrt(x*x + y*y + z*z);
        if (len > 0.001f) {
            x /= len;
            y /= len;
            z /= len;
        }
        
        return new float[]{x, y, z};
    }

    /** Smooth hermite interpolation (smoothstep). */
    private static float smoothstep(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t * t * (3.0f - 2.0f * t);
    }

    /** Linear interpolation. */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Get sun intensity for directional lighting (0.0 = night, 1.0 = midday).
     * This controls how strong the N·L directional lighting appears.
     * Different from sunBrightness which affects overall ambient light level.
     */
    public float getSunIntensity() {
        float[] dir = getSunDirection();
        float sunHeight = dir[1]; // Y component = how high sun is above horizon
        
        if (sunHeight > 0) {
            // Sun above horizon - intensity ramps up smoothly
            // Smoothstep from horizon (0) to high (0.5+)
            return smoothstep(Math.min(sunHeight * 2.0f, 1.0f));
        } else {
            // Sun below horizon - no directional light
            return 0.0f;
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
