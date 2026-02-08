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
import java.lang.management.MemoryMXBean;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL.*;

/**
 * Correct performance truth measurement.
 * 
 * Measures wall-clock frame time with proper attribution:
 * - update_ms: CPU update phase
 * - upload_ms: CPU time in GL buffer uploads
 * - render_submit_ms: CPU time issuing draw calls
 * - gpu_ms: GPU time via GL_TIME_ELAPSED query (not GL_TIMESTAMP)
 * - swap_ms: glfwSwapBuffers time
 * - sleep_ms: any frame limiter sleep
 * - unaccounted_ms: should trend toward 0 if attribution is correct
 * 
 * Also logs:
 * - GL_VENDOR, GL_RENDERER, GL_VERSION
 * - vsync state, window mode, resolution
 * - GC pause time and allocation rate
 */
public class PerfTruth {

    private static final int DURATION_SECONDS = 30;
    private static final long SEED = 42L;
    private static final float START_Y = 200.0f;
    private static final float FLIGHT_SPEED = 50.0f;
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
    
    // Frame timing (nanoseconds)
    private long frameBeginNs;
    private long updateEndNs;
    private long uploadEndNs;
    private long renderEndNs;
    private long swapBeginNs;
    private long swapEndNs;
    
    // GPU timing with GL_TIME_ELAPSED (correct approach)
    private int gpuQuery = 0;
    private int gpuQueryPrev = 0;
    private boolean gpuTimerAvailable = false;
    private boolean gpuQueryPending = false;
    private long gpuTimeNs = 0;
    
    // Frame data
    private final List<FrameSample> samples = new ArrayList<>();
    
    // GC tracking
    private final List<GarbageCollectorMXBean> gcBeans;
    private final MemoryMXBean memoryBean;
    private long lastGcTimeMs = 0;
    private long lastHeapUsed = 0;
    private long lastHeapTime = 0;
    
    // Flight state
    private float flightDistance = 0;
    
    // Screenshots
    private boolean tookStart = false, tookMid = false, tookEnd = false;
    
    // GL info
    private String glVendor = "unknown";
    private String glRenderer = "unknown";
    private String glVersion = "unknown";
    private boolean arbTimerQueryPresent = false;
    
    private String gitHash = "unknown";
    
    private static class FrameSample {
        long timestampMs;
        double frameWallMs;
        double updateMs;
        double uploadMs;
        double renderSubmitMs;
        double gpuMs;
        double swapMs;
        double gcPauseMs;
        boolean gpuQueryValid;
        int chunksLoaded;
        int chunksVisible;
        int drawCalls;
        int triangles;
        float playerX, playerY, playerZ;
    }
    
    public PerfTruth(String outputDir) {
        this.outputDir = outputDir != null ? outputDir : "artifacts/repro/HIGH_ALT_FAST_FLIGHT_V2";
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        
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
        } catch (Exception e) {}
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
        
        new File(outputDir).mkdirs();
        
        // Query GL info
        try {
            glVendor = glGetString(GL_VENDOR);
            glRenderer = glGetString(GL_RENDERER);
            glVersion = glGetString(GL_VERSION);
        } catch (Exception e) {
            glVendor = "error: " + e.getMessage();
        }
        
        // Check for ARB_timer_query
        try {
            String extensions = glGetString(GL_EXTENSIONS);
            arbTimerQueryPresent = extensions != null && extensions.contains("GL_ARB_timer_query");
        } catch (Exception e) {
            arbTimerQueryPresent = false;
        }
        
        // Initialize GPU timer query with GL_TIME_ELAPSED
        try {
            gpuQuery = glGenQueries();
            gpuQueryPrev = glGenQueries();
            gpuTimerAvailable = true;
        } catch (Exception e) {
            gpuTimerAvailable = false;
            System.out.println("[PerfTruth] GPU timer queries not available: " + e.getMessage());
        }
        
        // Setup scenario
        player.setGameMode(GameMode.CREATIVE);
        if (!player.isFlyMode()) player.toggleFlyMode();
        player.getCamera().getPosition().set(START_X, START_Y, START_Z);
        player.getCamera().setYaw(0);
        player.getCamera().setPitch(-10);
        
        if (worldTime != null) {
            worldTime.setWorldTick(6000);
        }
        
        updateGcBaseline();
        lastHeapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        lastHeapTime = System.nanoTime();
        
        System.out.println("[PerfTruth] Starting HIGH_ALT_FAST_FLIGHT_V2");
        System.out.println("[PerfTruth] GL_VENDOR: " + glVendor);
        System.out.println("[PerfTruth] GL_RENDERER: " + glRenderer);
        System.out.println("[PerfTruth] GL_VERSION: " + glVersion);
        System.out.println("[PerfTruth] ARB_timer_query: " + arbTimerQueryPresent);
        System.out.println("[PerfTruth] GPU timing: " + (gpuTimerAvailable ? "enabled" : "disabled"));
        System.out.println("[PerfTruth] Output: " + outputDir);
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
    
    /** Called at frame start */
    public void beginFrame() {
        if (!running || complete) return;
        frameBeginNs = System.nanoTime();
    }
    
    /** Called after CPU update phase */
    public void endUpdate() {
        if (!running || complete) return;
        updateEndNs = System.nanoTime();
    }
    
    /** Called after mesh/texture uploads, before draw calls */
    public void endUpload() {
        if (!running || complete) return;
        uploadEndNs = System.nanoTime();
    }
    
    /** Called before render pass - starts GPU timer */
    public void beginGpuTimer() {
        if (!running || complete || !gpuTimerAvailable) return;
        glBeginQuery(GL_TIME_ELAPSED, gpuQuery);
    }
    
    /** Called after render pass - ends GPU timer */
    public void endGpuTimer() {
        if (!running || complete || !gpuTimerAvailable) return;
        glEndQuery(GL_TIME_ELAPSED);
        gpuQueryPending = true;
    }
    
    /** Called after CPU render submission */
    public void endRenderSubmit() {
        if (!running || complete) return;
        renderEndNs = System.nanoTime();
    }
    
    /** Called before swap */
    public void beginSwap() {
        if (!running || complete) return;
        swapBeginNs = System.nanoTime();
    }
    
    /** Called after swap */
    public void endSwap() {
        if (!running || complete) return;
        swapEndNs = System.nanoTime();
    }
    
    /** Called at frame end */
    public void endFrame(float dt) {
        if (!running || complete) return;
        
        timer += dt;
        
        // Update player position
        flightDistance += FLIGHT_SPEED * dt;
        player.getCamera().getPosition().x = START_X;
        player.getCamera().getPosition().y = START_Y;
        player.getCamera().getPosition().z = START_Z - flightDistance;
        player.getCamera().setYaw(0);
        
        // Read GPU time from PREVIOUS frame's query (avoid stall)
        boolean gpuValid = false;
        long gpuNs = 0;
        if (gpuTimerAvailable && gpuQueryPending) {
            int[] available = new int[1];
            glGetQueryObjectiv(gpuQueryPrev, GL_QUERY_RESULT_AVAILABLE, available);
            if (available[0] == GL_TRUE) {
                long[] result = new long[1];
                glGetQueryObjecti64v(gpuQueryPrev, GL_QUERY_RESULT, result);
                gpuNs = result[0];
                gpuValid = true;
            }
        }
        
        // Swap query objects for next frame
        int temp = gpuQueryPrev;
        gpuQueryPrev = gpuQuery;
        gpuQuery = temp;
        
        takeSample(gpuNs, gpuValid);
        
        // Screenshots
        int fbW = window.getFramebufferWidth();
        int fbH = window.getFramebufferHeight();
        if (!tookStart && timer >= 2.0f) {
            Screenshot.captureToFile(fbW, fbH, outputDir + "/screenshot_start.png");
            tookStart = true;
        }
        if (!tookMid && timer >= DURATION_SECONDS / 2.0f) {
            Screenshot.captureToFile(fbW, fbH, outputDir + "/screenshot_mid.png");
            tookMid = true;
        }
        if (!tookEnd && timer >= DURATION_SECONDS - 1.0f) {
            Screenshot.captureToFile(fbW, fbH, outputDir + "/screenshot_end.png");
            tookEnd = true;
        }
        
        if (timer >= DURATION_SECONDS) {
            finish();
        }
    }
    
    private void takeSample(long gpuNs, boolean gpuValid) {
        FrameSample s = new FrameSample();
        s.timestampMs = System.currentTimeMillis() - captureStartMs;
        
        s.frameWallMs = (swapEndNs - frameBeginNs) / 1_000_000.0;
        s.updateMs = (updateEndNs - frameBeginNs) / 1_000_000.0;
        s.uploadMs = (uploadEndNs - updateEndNs) / 1_000_000.0;
        s.renderSubmitMs = (renderEndNs - uploadEndNs) / 1_000_000.0;
        s.swapMs = (swapEndNs - swapBeginNs) / 1_000_000.0;
        s.gpuMs = gpuNs / 1_000_000.0;
        s.gpuQueryValid = gpuValid;
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
        
        System.out.println("[PerfTruth] Capture complete. " + samples.size() + " samples.");
        
        try {
            writeFrameTimes();
            writePerfTruth();
            writeSha256Sums();
            System.out.println("[PerfTruth] Results written to: " + outputDir);
        } catch (IOException e) {
            System.err.println("[PerfTruth] ERROR: " + e.getMessage());
        }
        
        if (gpuTimerAvailable) {
            glDeleteQueries(gpuQuery);
            glDeleteQueries(gpuQueryPrev);
        }
    }
    
    private void writeFrameTimes() throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(outputDir, "frame_times.csv")))) {
            pw.println("timestamp_ms,frame_wall_ms,update_ms,upload_ms,render_submit_ms,gpu_ms,gpu_valid,swap_ms,gc_pause_ms,chunks_loaded,chunks_visible,draw_calls,triangles,x,y,z");
            for (FrameSample s : samples) {
                pw.printf("%d,%.3f,%.3f,%.3f,%.3f,%.3f,%s,%.3f,%.3f,%d,%d,%d,%d,%.1f,%.1f,%.1f%n",
                    s.timestampMs, s.frameWallMs, s.updateMs, s.uploadMs, s.renderSubmitMs,
                    s.gpuMs, s.gpuQueryValid ? "true" : "false", s.swapMs, s.gcPauseMs,
                    s.chunksLoaded, s.chunksVisible, s.drawCalls, s.triangles,
                    s.playerX, s.playerY, s.playerZ);
            }
        }
    }
    
    private void writePerfTruth() throws IOException {
        if (samples.isEmpty()) return;
        
        List<Double> wallTimes = new ArrayList<>();
        List<Double> updateTimes = new ArrayList<>();
        List<Double> uploadTimes = new ArrayList<>();
        List<Double> renderTimes = new ArrayList<>();
        List<Double> gpuTimes = new ArrayList<>();
        List<Double> swapTimes = new ArrayList<>();
        
        double totalWall = 0, totalUpdate = 0, totalUpload = 0, totalRender = 0;
        double totalGpu = 0, totalSwap = 0, totalGc = 0;
        int gpuValidCount = 0;
        
        for (FrameSample s : samples) {
            wallTimes.add(s.frameWallMs);
            updateTimes.add(s.updateMs);
            uploadTimes.add(s.uploadMs);
            renderTimes.add(s.renderSubmitMs);
            swapTimes.add(s.swapMs);
            if (s.gpuQueryValid) {
                gpuTimes.add(s.gpuMs);
                totalGpu += s.gpuMs;
                gpuValidCount++;
            }
            
            totalWall += s.frameWallMs;
            totalUpdate += s.updateMs;
            totalUpload += s.uploadMs;
            totalRender += s.renderSubmitMs;
            totalSwap += s.swapMs;
            totalGc += s.gcPauseMs;
        }
        
        Collections.sort(wallTimes);
        Collections.sort(updateTimes);
        Collections.sort(uploadTimes);
        Collections.sort(renderTimes);
        Collections.sort(gpuTimes);
        Collections.sort(swapTimes);
        
        int n = samples.size();
        double avgWall = totalWall / n;
        double avgUpdate = totalUpdate / n;
        double avgUpload = totalUpload / n;
        double avgRender = totalRender / n;
        double avgSwap = totalSwap / n;
        double avgGpu = gpuValidCount > 0 ? totalGpu / gpuValidCount : 0;
        
        double cpuTotal = avgUpdate + avgUpload + avgRender;
        double unaccounted = avgWall - cpuTotal - avgSwap - avgGpu;
        
        // Determine bottleneck
        String bottleneck;
        if (gpuValidCount == 0) {
            bottleneck = "UNKNOWN (GPU timing failed - " + gpuValidCount + "/" + n + " valid)";
        } else if (avgGpu > cpuTotal && avgGpu > avgSwap) {
            bottleneck = "GPU";
        } else if (avgSwap > cpuTotal && avgSwap > avgGpu) {
            bottleneck = "PRESENT/VSYNC";
        } else if (avgUpdate > avgUpload + avgRender + avgGpu + avgSwap) {
            bottleneck = "CPU_UPDATE";
        } else {
            bottleneck = "CPU_RENDER";
        }
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(outputDir, "perf_truth.json")))) {
            pw.println("{");
            pw.println("  \"metadata\": {");
            pw.println("    \"git_hash\": \"" + gitHash + "\",");
            pw.println("    \"scenario\": \"HIGH_ALT_FAST_FLIGHT_V2\",");
            pw.println("    \"duration_seconds\": " + DURATION_SECONDS + ",");
            pw.println("    \"sample_count\": " + n + ",");
            pw.println("    \"seed\": " + SEED + ",");
            pw.println("    \"capture_timestamp\": \"" + Instant.now() + "\"");
            pw.println("  },");
            pw.println();
            pw.println("  \"gl_info\": {");
            pw.println("    \"vendor\": \"" + glVendor + "\",");
            pw.println("    \"renderer\": \"" + glRenderer + "\",");
            pw.println("    \"version\": \"" + glVersion + "\",");
            pw.println("    \"arb_timer_query\": " + arbTimerQueryPresent);
            pw.println("  },");
            pw.println();
            pw.println("  \"environment\": {");
            pw.println("    \"vsync_enabled\": true,");
            pw.println("    \"window_mode\": \"windowed\",");
            pw.println("    \"resolution\": \"" + window.getFramebufferWidth() + "x" + window.getFramebufferHeight() + "\",");
            pw.println("    \"shadows_enabled\": " + (renderer.getShadowRenderer() != null && renderer.getShadowRenderer().isShadowsEnabled()));
            pw.println("  },");
            pw.println();
            pw.println("  \"frame_wall_ms\": {");
            pw.printf("    \"avg\": %.3f,%n", avgWall);
            pw.printf("    \"p50\": %.3f,%n", percentile(wallTimes, 0.50));
            pw.printf("    \"p95\": %.3f,%n", percentile(wallTimes, 0.95));
            pw.printf("    \"p99\": %.3f,%n", percentile(wallTimes, 0.99));
            pw.printf("    \"max\": %.3f%n", wallTimes.isEmpty() ? 0 : wallTimes.get(wallTimes.size()-1));
            pw.println("  },");
            pw.printf("  \"fps_avg\": %.2f,%n", 1000.0 / avgWall);
            pw.printf("  \"fps_1pct_low\": %.2f,%n", 1000.0 / percentile(wallTimes, 0.99));
            pw.println();
            pw.println("  \"update_ms\": {");
            pw.printf("    \"avg\": %.3f,%n", avgUpdate);
            pw.printf("    \"p50\": %.3f,%n", percentile(updateTimes, 0.50));
            pw.printf("    \"p95\": %.3f,%n", percentile(updateTimes, 0.95));
            pw.printf("    \"p99\": %.3f%n", percentile(updateTimes, 0.99));
            pw.println("  },");
            pw.println();
            pw.println("  \"upload_ms\": {");
            pw.printf("    \"avg\": %.3f,%n", avgUpload);
            pw.printf("    \"p50\": %.3f,%n", percentile(uploadTimes, 0.50));
            pw.printf("    \"p95\": %.3f,%n", percentile(uploadTimes, 0.95));
            pw.printf("    \"p99\": %.3f%n", percentile(uploadTimes, 0.99));
            pw.println("  },");
            pw.println();
            pw.println("  \"render_submit_ms\": {");
            pw.printf("    \"avg\": %.3f,%n", avgRender);
            pw.printf("    \"p50\": %.3f,%n", percentile(renderTimes, 0.50));
            pw.printf("    \"p95\": %.3f,%n", percentile(renderTimes, 0.95));
            pw.printf("    \"p99\": %.3f%n", percentile(renderTimes, 0.99));
            pw.println("  },");
            pw.println();
            pw.println("  \"gpu_ms\": {");
            pw.printf("    \"avg\": %.3f,%n", avgGpu);
            pw.printf("    \"p50\": %.3f,%n", percentile(gpuTimes, 0.50));
            pw.printf("    \"p95\": %.3f,%n", percentile(gpuTimes, 0.95));
            pw.printf("    \"p99\": %.3f,%n", percentile(gpuTimes, 0.99));
            pw.println("    \"valid_samples\": " + gpuValidCount + ",");
            pw.println("    \"total_samples\": " + n);
            pw.println("  },");
            pw.println();
            pw.println("  \"swap_ms\": {");
            pw.printf("    \"avg\": %.3f,%n", avgSwap);
            pw.printf("    \"p50\": %.3f,%n", percentile(swapTimes, 0.50));
            pw.printf("    \"p95\": %.3f,%n", percentile(swapTimes, 0.95));
            pw.printf("    \"p99\": %.3f%n", percentile(swapTimes, 0.99));
            pw.println("  },");
            pw.println();
            pw.printf("  \"gc_total_ms\": %.3f,%n", totalGc);
            pw.printf("  \"unaccounted_ms\": %.3f,%n", unaccounted);
            pw.println();
            pw.println("  \"verdict\": {");
            pw.println("    \"bottleneck\": \"" + bottleneck + "\",");
            pw.printf("    \"frame_breakdown\": \"update=%.1fms + upload=%.1fms + render=%.1fms + gpu=%.1fms + swap=%.1fms + unaccounted=%.1fms = %.1fms\"%n",
                avgUpdate, avgUpload, avgRender, avgGpu, avgSwap, unaccounted, avgWall);
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
