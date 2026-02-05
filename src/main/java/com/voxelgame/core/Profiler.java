package com.voxelgame.core;

import java.util.*;

/**
 * Lightweight profiler for measuring subsystem timings.
 * Tracks per-frame costs of worldgen, meshing, rendering, etc.
 * Thread-safe and supports nested sections.
 */
public class Profiler {
    
    private static final int HISTORY_SIZE = 60; // Rolling average over 60 frames
    
    /** Singleton instance */
    private static final Profiler INSTANCE = new Profiler();
    
    /** Active timing sections (section name -> start time in nanos) */
    private final Map<String, Long> activeSections = new HashMap<>();
    
    /** Current frame timings (section name -> duration in nanos) */
    private final Map<String, Long> currentFrameTimings = new LinkedHashMap<>();
    
    /** Rolling history of frame timings (section name -> circular buffer of durations) */
    private final Map<String, long[]> timingHistory = new HashMap<>();
    
    /** Current position in circular buffers */
    private int historyIndex = 0;
    
    /** Number of frames recorded (for accurate averages before buffer fills) */
    private int framesRecorded = 0;
    
    /** Order in which sections were first seen (for consistent display order) */
    private final List<String> sectionOrder = new ArrayList<>();
    
    /** Lock for thread safety */
    private final Object lock = new Object();
    
    private Profiler() {}
    
    public static Profiler getInstance() {
        return INSTANCE;
    }
    
    /**
     * Begin timing a section. Call end() with the same section name when done.
     * Sections can be nested.
     */
    public void begin(String section) {
        synchronized (lock) {
            activeSections.put(section, System.nanoTime());
        }
    }
    
    /**
     * End timing a section and record the duration.
     */
    public void end(String section) {
        long endTime = System.nanoTime();
        synchronized (lock) {
            Long startTime = activeSections.remove(section);
            if (startTime != null) {
                long duration = endTime - startTime;
                // Accumulate if section called multiple times per frame
                currentFrameTimings.merge(section, duration, Long::sum);
                
                // Track section order for consistent display
                if (!sectionOrder.contains(section)) {
                    sectionOrder.add(section);
                }
            }
        }
    }
    
    /**
     * Call at the end of each frame to commit current timings to history.
     */
    public void endFrame() {
        synchronized (lock) {
            // Commit current frame timings to history
            for (Map.Entry<String, Long> entry : currentFrameTimings.entrySet()) {
                String section = entry.getKey();
                long duration = entry.getValue();
                
                long[] history = timingHistory.computeIfAbsent(section, k -> new long[HISTORY_SIZE]);
                history[historyIndex] = duration;
            }
            
            // Zero out sections not recorded this frame
            for (String section : timingHistory.keySet()) {
                if (!currentFrameTimings.containsKey(section)) {
                    timingHistory.get(section)[historyIndex] = 0;
                }
            }
            
            // Clear current frame and advance index
            currentFrameTimings.clear();
            historyIndex = (historyIndex + 1) % HISTORY_SIZE;
            framesRecorded = Math.min(framesRecorded + 1, HISTORY_SIZE);
        }
    }
    
    /**
     * Get the last frame's timing for a section in milliseconds.
     */
    public double getLastFrameMs(String section) {
        synchronized (lock) {
            long[] history = timingHistory.get(section);
            if (history == null || framesRecorded == 0) return 0.0;
            int lastIndex = (historyIndex - 1 + HISTORY_SIZE) % HISTORY_SIZE;
            return history[lastIndex] / 1_000_000.0;
        }
    }
    
    /**
     * Get the rolling average timing for a section in milliseconds.
     */
    public double getAverageMs(String section) {
        synchronized (lock) {
            long[] history = timingHistory.get(section);
            if (history == null || framesRecorded == 0) return 0.0;
            
            long sum = 0;
            int count = Math.min(framesRecorded, HISTORY_SIZE);
            for (int i = 0; i < count; i++) {
                sum += history[i];
            }
            return (sum / (double) count) / 1_000_000.0;
        }
    }
    
    /**
     * Get the max timing over the history window in milliseconds.
     */
    public double getMaxMs(String section) {
        synchronized (lock) {
            long[] history = timingHistory.get(section);
            if (history == null || framesRecorded == 0) return 0.0;
            
            long max = 0;
            int count = Math.min(framesRecorded, HISTORY_SIZE);
            for (int i = 0; i < count; i++) {
                max = Math.max(max, history[i]);
            }
            return max / 1_000_000.0;
        }
    }
    
    /**
     * Get all tracked sections in the order they were first recorded.
     */
    public List<String> getSections() {
        synchronized (lock) {
            return new ArrayList<>(sectionOrder);
        }
    }
    
    /**
     * Get formatted timing strings for display, sorted by average time (most expensive first).
     * Format: "Section: X.Xms (avg: X.Xms)"
     * 
     * @param maxLines Maximum number of lines to return
     * @return List of formatted timing strings
     */
    public List<String> getTimings(int maxLines) {
        synchronized (lock) {
            if (framesRecorded == 0 || sectionOrder.isEmpty()) {
                return Collections.emptyList();
            }
            
            // Build list of (section, avgMs) pairs
            List<Map.Entry<String, Double>> sorted = new ArrayList<>();
            for (String section : sectionOrder) {
                double avgMs = getAverageMs(section);
                sorted.add(new AbstractMap.SimpleEntry<>(section, avgMs));
            }
            
            // Sort by average time descending
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            
            // Format output
            List<String> result = new ArrayList<>();
            int count = Math.min(maxLines, sorted.size());
            for (int i = 0; i < count; i++) {
                String section = sorted.get(i).getKey();
                double lastMs = getLastFrameMs(section);
                double avgMs = sorted.get(i).getValue();
                result.add(String.format("%s: %.2fms (avg: %.2fms)", section, lastMs, avgMs));
            }
            return result;
        }
    }
    
    /**
     * Get formatted timing strings with max timing shown.
     * Format: "Section: X.Xms (avg: X.Xms, max: X.Xms)"
     */
    public List<String> getTimingsWithMax(int maxLines) {
        synchronized (lock) {
            if (framesRecorded == 0 || sectionOrder.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<Map.Entry<String, Double>> sorted = new ArrayList<>();
            for (String section : sectionOrder) {
                double avgMs = getAverageMs(section);
                sorted.add(new AbstractMap.SimpleEntry<>(section, avgMs));
            }
            
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            
            List<String> result = new ArrayList<>();
            int count = Math.min(maxLines, sorted.size());
            for (int i = 0; i < count; i++) {
                String section = sorted.get(i).getKey();
                double lastMs = getLastFrameMs(section);
                double avgMs = sorted.get(i).getValue();
                double maxMs = getMaxMs(section);
                result.add(String.format("%s: %.2fms (avg: %.2f, max: %.2f)", 
                    section, lastMs, avgMs, maxMs));
            }
            return result;
        }
    }
    
    /**
     * Reset all profiling data.
     */
    public void reset() {
        synchronized (lock) {
            activeSections.clear();
            currentFrameTimings.clear();
            timingHistory.clear();
            sectionOrder.clear();
            historyIndex = 0;
            framesRecorded = 0;
        }
    }
}
