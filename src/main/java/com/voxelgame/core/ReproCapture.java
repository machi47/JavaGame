package com.voxelgame.core;

import com.voxelgame.platform.Window;
import com.voxelgame.render.Renderer;
import com.voxelgame.sim.GameMode;
import com.voxelgame.sim.Player;
import com.voxelgame.ui.Screenshot;
import com.voxelgame.world.WorldTime;
import com.voxelgame.world.stream.ChunkManager;

import java.io.*;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.lwjgl.opengl.GL33.*;

/**
 * Reproducible capture mode for HIGH_ALT_FAST_FLIGHT_V2.
 * 
 * Deterministic:
 * - Fixed seed: 42
 * - Fixed camera pose and path
 * - Fixed speed: 50 blocks/sec (~3 chunks/sec)
 * - Fixed time of day: noon
 * 
 * Outputs:
 * - perf_truth.json: wall-clock + swap + GPU timing with percentiles
 * - frame_times.csv: per-frame breakdown
 * - screenshot_start.png, screenshot_mid.png, screenshot_end.png
 * - SHA256SUMS.txt
 */
public class ReproCapture {

    private static final int DURATION_SECONDS = 30;
    private static final long SEED = 42L;
    private static final float START_Y = 200.0f;  // High altitude as user describes
    private static final float FLIGHT_SPEED = 50.0f;  // ~3 chunks/sec
    
    // Fixed start position
    private static final float START_X = 0.0f;
    private static final float START_Z = 0.0f;
    
    private final String outputDir;
    
    private Player player;
    private WorldTime worldTime;
    private Renderer renderer;
    private ChunkManager chunkManager;
    private Window window;
    
    private boolean running = false;
    private boolean complete = false;
    private float timer = 0;
    private long captureStartMs;
    
    // Frame timing
    private long frameBeginNs;
    private long cpuUpdateEndNs;
    private long cpuRenderEndNs;
    private long swapBeginNs;
    private long swapEndNs;
    
    // GPU timer queries
    private int[] gpuQueryIds = new int[4];
    private int currentQueryPair = 0;
    private boolean gpuTimingAvailable = false;
    private long lastGpuTimeNs = 0;
    
    // Frame data
    private final List<FrameSample> samples = new ArrayList<>();
    
    // GC tracking
    private final List<GarbageCollectorMXBean> gcBeans;
    private long lastGcTimeMs = 0;
    
    // Flight state
    private float flightDistance = 0;
    
    // Screenshots
    private boolean tookStartScreenshot = false;
    private boolean tookMidScreenshot = false;
    private boolean tookEndScreenshot = false;
    
    private String gitHash = "unknown";
    
    private static class FrameSample {
        long timestampMs;
        double frameWallMs;
        double cpuUpdateMs;
        double cpuRenderMs;
        double swapMs;
        double gpuMs;
        double gcPauseMs;
        int chunksLoaded;
        int chunksVisible;
        int drawCalls;
        int triangles;
        float playerX, playerY, playerZ;
    }
    
    public ReproCapture(String outputDir) {
        this.outputDir = outputDir != null ? outputDir : "artifacts/repro/HIGH_ALT_FAST_FLIGHT_V2";
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        // Get git hash
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--short", "HEAD");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            if (line != null && line.length() >= 7) {
                gitHash = line.trim();
            }
            p.waitFor();
        } catch (Exception e) {
            // ignore
        }
    }
    
    public long getSeed() {
        return SEED;
    }
    
    public void setReferences(Player player, WorldTime worldTime, Renderer renderer, 
                              ChunkManager chunkManager, Window window) {
        this.player = player;
        this.worldTime = worldTime;
        this.renderer = renderer;
        this.chunkManager = chunkManager;
        this.window = window;
    }
    
    public boolean isComplete() {
        return complete;
    }
    
    public void start() {
        if (running) return;
        running = true;
        timer = 0;
        captureStartMs = System.currentTimeMillis();
        
        // Create output directory
        new File(outputDir).mkdirs();
        
        // Initialize GPU timer queries
        initGpuTimerQueries();
        
        // Setup scenario
        player.setGameMode(GameMode.CREATIVE);
        if (!player.isFlyMode()) player.toggleFlyMode();
        
        // Set deterministic position
        player.getCamera().getPosition().set(START_X, START_Y, START_Z);
        player.getCamera().setYaw(0);  // Looking north
        player.getCamera().setPitch(-10);  // Looking slightly down to see terrain
        
        // Set time to noon
        if (worldTime != null) {
            worldTime.setWorldTick(6000);
        }
        
        updateGcBaseline();
        
        System.out.println("[ReproCapture] Starting HIGH_ALT_FAST_FLIGHT_V2");
        System.out.println("[ReproCapture] Seed: " + SEED);
        System.out.println("[ReproCapture] Duration: " + DURATION_SECONDS + "s at " + FLIGHT_SPEED + " blocks/sec");
        System.out.println("[ReproCapture] Output: " + outputDir);
        System.out.println("[ReproCapture] GPU timing: " + (gpuTimingAvailable ? "enabled" : "disabled"));
    }
    
    private void initGpuTimerQueries() {
        try {
            for (int i = 0; i < 4; i++) {
                gpuQueryIds[i] = glGenQueries();
            }
            gpuTimingAvailable = true;
            glQueryCounter(gpuQueryIds[0], GL_TIMESTAMP);
            glQueryCounter(gpuQueryIds[1], GL_TIMESTAMP);
        } catch (Exception e) {
            gpuTimingAvailable = false;
        }
    }
    
    private void updateGcBaseline() {
        long totalTime = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            totalTime += gc.getCollectionTime();
        }
        lastGcTimeMs = totalTime;
    }
    
    private double getGcPauseSinceLastSample() {
        long totalTime = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            totalTime += gc.getCollectionTime();
        }
        double pauseMs = totalTime - lastGcTimeMs;
        lastGcTimeMs = totalTime;
        return pauseMs;
    }
    
    public void beginFrame() {
        if (!running || complete) return;
        frameBeginNs = System.nanoTime();
        
        if (gpuTimingAvailable) {
            glQueryCounter(gpuQueryIds[currentQueryPair * 2], GL_TIMESTAMP);
        }
    }
    
    public void endCpuUpdate() {
        if (!running || complete) return;
        cpuUpdateEndNs = System.nanoTime();
    }
    
    public void endCpuRender() {
        if (!running || complete) return;
        cpuRenderEndNs = System.nanoTime();
        
        if (gpuTimingAvailable) {
            glQueryCounter(gpuQueryIds[currentQueryPair * 2 + 1], GL_TIMESTAMP);
        }
    }
    
    public void beginSwap() {
        if (!running || complete) return;
        swapBeginNs = System.nanoTime();
    }
    
    public void endSwap() {
        if (!running || complete) return;
        swapEndNs = System.nanoTime();
    }
    
    public void endFrame(float dt) {
        if (!running || complete) return;
        
        timer += dt;
        
        // Update player position: fly straight forward (north, -Z direction)
        flightDistance += FLIGHT_SPEED * dt;
        player.getCamera().getPosition().x = START_X;
        player.getCamera().getPosition().y = START_Y;
        player.getCamera().getPosition().z = START_Z - flightDistance;  // Fly north
        player.getCamera().setYaw(0);  // Keep looking north
        
        // Read GPU time from previous frame
        long gpuTimeNs = 0;
        if (gpuTimingAvailable && samples.size() > 0) {
            int prevPair = 1 - currentQueryPair;
            int startQuery = gpuQueryIds[prevPair * 2];
            int endQuery = gpuQueryIds[prevPair * 2 + 1];
            
            int[] available = new int[1];
            glGetQueryObjectiv(endQuery, GL_QUERY_RESULT_AVAILABLE, available);
            
            if (available[0] == GL_TRUE) {
                long[] startTime = new long[1];
                long[] endTime = new long[1];
                glGetQueryObjecti64v(startQuery, GL_QUERY_RESULT, startTime);
                glGetQueryObjecti64v(endQuery, GL_QUERY_RESULT, endTime);
                gpuTimeNs = endTime[0] - startTime[0];
                lastGpuTimeNs = gpuTimeNs;
            } else {
                gpuTimeNs = lastGpuTimeNs;
            }
        }
        currentQueryPair = 1 - currentQueryPair;
        
        takeSample(gpuTimeNs);
        
        // Take screenshots at start, mid, and end
        int fbW = window.getFramebufferWidth();
        int fbH = window.getFramebufferHeight();
        
        if (!tookStartScreenshot && timer >= 2.0f) {
            takeScreenshot("screenshot_start.png", fbW, fbH);
            tookStartScreenshot = true;
        }
        if (!tookMidScreenshot && timer >= DURATION_SECONDS / 2.0f) {
            takeScreenshot("screenshot_mid.png", fbW, fbH);
            tookMidScreenshot = true;
        }
        if (!tookEndScreenshot && timer >= DURATION_SECONDS - 1.0f) {
            takeScreenshot("screenshot_end.png", fbW, fbH);
            tookEndScreenshot = true;
        }
        
        if (timer >= DURATION_SECONDS) {
            finish();
        }
    }
    
    private void takeScreenshot(String filename, int width, int height) {
        String path = Screenshot.captureToFile(width, height, outputDir + "/" + filename);
        if (path != null) {
            System.out.println("[ReproCapture] Screenshot: " + path);
        }
    }
    
    private void takeSample(long gpuTimeNs) {
        FrameSample s = new FrameSample();
        s.timestampMs = System.currentTimeMillis() - captureStartMs;
        s.frameWallMs = (swapEndNs - frameBeginNs) / 1_000_000.0;
        s.cpuUpdateMs = (cpuUpdateEndNs - frameBeginNs) / 1_000_000.0;
        s.cpuRenderMs = (cpuRenderEndNs - cpuUpdateEndNs) / 1_000_000.0;
        s.swapMs = (swapEndNs - swapBeginNs) / 1_000_000.0;
        s.gpuMs = gpuTimeNs / 1_000_000.0;
        s.gcPauseMs = getGcPauseSinceLastSample();
        
        if (chunkManager != null) {
            s.chunksLoaded = chunkManager.getTotalChunks();
        }
        if (renderer != null) {
            s.chunksVisible = renderer.getRenderedChunks();
            s.drawCalls = renderer.getDrawCalls();
            s.triangles = renderer.getTriangleCount();
        }
        
        s.playerX = player.getCamera().getPosition().x;
        s.playerY = player.getCamera().getPosition().y;
        s.playerZ = player.getCamera().getPosition().z;
        
        samples.add(s);
    }
    
    private void finish() {
        running = false;
        complete = true;
        
        System.out.println("[ReproCapture] Capture complete. " + samples.size() + " samples.");
        
        try {
            writeFrameTimes();
            writePerfTruth();
            writeSha256Sums();
            System.out.println("[ReproCapture] Results written to: " + outputDir);
        } catch (IOException e) {
            System.err.println("[ReproCapture] ERROR: " + e.getMessage());
        }
        
        if (gpuTimingAvailable) {
            for (int id : gpuQueryIds) {
                glDeleteQueries(id);
            }
        }
    }
    
    private void writeFrameTimes() throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(outputDir, "frame_times.csv")))) {
            pw.println("timestamp_ms,frame_wall_ms,cpu_update_ms,cpu_render_ms,swap_ms,gpu_ms,gc_pause_ms,chunks_loaded,chunks_visible,draw_calls,triangles,x,y,z");
            for (FrameSample s : samples) {
                pw.printf("%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%d,%.1f,%.1f,%.1f%n",
                    s.timestampMs, s.frameWallMs, s.cpuUpdateMs, s.cpuRenderMs, 
                    s.swapMs, s.gpuMs, s.gcPauseMs,
                    s.chunksLoaded, s.chunksVisible, s.drawCalls, s.triangles,
                    s.playerX, s.playerY, s.playerZ);
            }
        }
    }
    
    private void writePerfTruth() throws IOException {
        if (samples.isEmpty()) return;
        
        List<Double> frameWallTimes = new ArrayList<>();
        List<Double> swapTimes = new ArrayList<>();
        List<Double> gpuTimes = new ArrayList<>();
        List<Double> cpuUpdateTimes = new ArrayList<>();
        List<Double> cpuRenderTimes = new ArrayList<>();
        
        double totalWall = 0, totalSwap = 0, totalGpu = 0, totalGc = 0;
        double maxWall = 0, maxSwap = 0, maxGpu = 0;
        
        for (FrameSample s : samples) {
            frameWallTimes.add(s.frameWallMs);
            swapTimes.add(s.swapMs);
            gpuTimes.add(s.gpuMs);
            cpuUpdateTimes.add(s.cpuUpdateMs);
            cpuRenderTimes.add(s.cpuRenderMs);
            
            totalWall += s.frameWallMs;
            totalSwap += s.swapMs;
            totalGpu += s.gpuMs;
            totalGc += s.gcPauseMs;
            
            maxWall = Math.max(maxWall, s.frameWallMs);
            maxSwap = Math.max(maxSwap, s.swapMs);
            maxGpu = Math.max(maxGpu, s.gpuMs);
        }
        
        Collections.sort(frameWallTimes);
        Collections.sort(swapTimes);
        Collections.sort(gpuTimes);
        Collections.sort(cpuUpdateTimes);
        Collections.sort(cpuRenderTimes);
        
        int n = samples.size();
        
        double p50Wall = percentile(frameWallTimes, 0.50);
        double p95Wall = percentile(frameWallTimes, 0.95);
        double p99Wall = percentile(frameWallTimes, 0.99);
        double onePercentLowMs = percentile(frameWallTimes, 0.99);
        double onePercentLowFps = 1000.0 / onePercentLowMs;
        
        double p50Swap = percentile(swapTimes, 0.50);
        double p95Swap = percentile(swapTimes, 0.95);
        double p99Swap = percentile(swapTimes, 0.99);
        
        double p50Gpu = percentile(gpuTimes, 0.50);
        double p95Gpu = percentile(gpuTimes, 0.95);
        double p99Gpu = percentile(gpuTimes, 0.99);
        
        double avgWall = totalWall / n;
        double avgFps = 1000.0 / avgWall;
        double avgSwap = totalSwap / n;
        double avgGpu = totalGpu / n;
        double avgCpuUpdate = cpuUpdateTimes.stream().mapToDouble(d -> d).average().orElse(0);
        double avgCpuRender = cpuRenderTimes.stream().mapToDouble(d -> d).average().orElse(0);
        
        // Determine bottleneck
        double cpuTotal = avgCpuUpdate + avgCpuRender;
        double unaccounted = avgWall - cpuTotal - avgSwap;
        
        String bottleneck;
        if (unaccounted > cpuTotal && unaccounted > avgSwap) {
            bottleneck = "GPU (unaccounted time: " + String.format("%.1f", unaccounted) + "ms)";
        } else if (avgSwap > cpuTotal) {
            bottleneck = "PRESENT/VSYNC";
        } else {
            bottleneck = "CPU";
        }
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(outputDir, "perf_truth.json")))) {
            pw.println("{");
            pw.println("  \"metadata\": {");
            pw.println("    \"git_hash\": \"" + gitHash + "\",");
            pw.println("    \"scenario\": \"HIGH_ALT_FAST_FLIGHT_V2\",");
            pw.println("    \"duration_seconds\": " + DURATION_SECONDS + ",");
            pw.println("    \"sample_count\": " + n + ",");
            pw.println("    \"seed\": " + SEED + ",");
            pw.println("    \"flight_speed\": " + FLIGHT_SPEED + ",");
            pw.println("    \"altitude\": " + START_Y + ",");
            pw.println("    \"capture_timestamp\": \"" + Instant.now() + "\"");
            pw.println("  },");
            pw.println();
            pw.println("  \"environment\": {");
            pw.println("    \"vsync_enabled\": true,");
            pw.println("    \"window_mode\": \"windowed\",");
            pw.println("    \"resolution\": \"" + window.getFramebufferWidth() + "x" + window.getFramebufferHeight() + "\",");
            pw.println("    \"shadows_enabled\": " + (renderer.getShadowRenderer() != null && renderer.getShadowRenderer().isShadowsEnabled()) + ",");
            pw.println("    \"gpu_timing_available\": " + gpuTimingAvailable);
            pw.println("  },");
            pw.println();
            pw.println("  \"frame_wall_ms\": {");
            pw.printf("    \"avg\": %.3f,%n", avgWall);
            pw.printf("    \"p50\": %.3f,%n", p50Wall);
            pw.printf("    \"p95\": %.3f,%n", p95Wall);
            pw.printf("    \"p99\": %.3f,%n", p99Wall);
            pw.printf("    \"max\": %.3f%n", maxWall);
            pw.println("  },");
            pw.println();
            pw.printf("  \"fps_avg\": %.2f,%n", avgFps);
            pw.printf("  \"fps_1pct_low\": %.2f,%n", onePercentLowFps);
            pw.println();
            pw.println("  \"swap_ms\": {");
            pw.printf("    \"avg\": %.3f,%n", avgSwap);
            pw.printf("    \"p50\": %.3f,%n", p50Swap);
            pw.printf("    \"p95\": %.3f,%n", p95Swap);
            pw.printf("    \"p99\": %.3f,%n", p99Swap);
            pw.printf("    \"max\": %.3f%n", maxSwap);
            pw.println("  },");
            pw.println();
            pw.println("  \"gpu_ms\": {");
            pw.printf("    \"avg\": %.3f,%n", avgGpu);
            pw.printf("    \"p50\": %.3f,%n", p50Gpu);
            pw.printf("    \"p95\": %.3f,%n", p95Gpu);
            pw.printf("    \"p99\": %.3f,%n", p99Gpu);
            pw.printf("    \"max\": %.3f%n", maxGpu);
            pw.println("  },");
            pw.println();
            pw.println("  \"cpu_ms\": {");
            pw.printf("    \"update_avg\": %.3f,%n", avgCpuUpdate);
            pw.printf("    \"render_avg\": %.3f,%n", avgCpuRender);
            pw.printf("    \"total_avg\": %.3f%n", cpuTotal);
            pw.println("  },");
            pw.println();
            pw.printf("  \"unaccounted_ms\": %.3f,%n", unaccounted);
            pw.printf("  \"gc_total_ms\": %.3f,%n", totalGc);
            pw.println();
            pw.println("  \"verdict\": {");
            pw.println("    \"bottleneck\": \"" + bottleneck + "\",");
            pw.printf("    \"frame_breakdown\": \"cpu=%.1fms + swap=%.1fms + unaccounted=%.1fms = %.1fms\"%n",
                cpuTotal, avgSwap, unaccounted, avgWall);
            pw.println("  }");
            pw.println("}");
        }
    }
    
    private void writeSha256Sums() throws IOException {
        File dir = new File(outputDir);
        File[] files = dir.listFiles((d, name) -> 
            name.endsWith(".json") || name.endsWith(".csv") || name.endsWith(".png"));
        if (files == null) return;
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(outputDir, "SHA256SUMS.txt")))) {
            for (File file : files) {
                String hash = sha256(file);
                pw.println(hash + "  " + file.getName());
            }
        }
    }
    
    private String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "error";
        }
    }
    
    private double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) (sorted.size() * p);
        return sorted.get(Math.min(idx, sorted.size() - 1));
    }
}
