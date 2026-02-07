package com.voxelgame.benchmark;

import com.voxelgame.render.Camera;
import com.voxelgame.world.World;
import com.voxelgame.world.stream.ChunkManager;

import java.io.*;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public class WorldBenchmark {
    public static final long FIXED_SEED = 42L;
    public static final float DURATION_SECONDS = 60.0f;
    public static final float SAMPLE_INTERVAL = 0.1f;
    public static final float CAMERA_SPEED = 20.0f;
    
    private static final float[][] WAYPOINTS = {{0,80,0},{200,80,0},{200,80,200},{-200,80,200},{-200,80,-200},{400,80,-200},{400,80,400},{-400,80,400}};
    
    private final String outputDir, phase;
    private float elapsedTime = 0, sampleTimer = 0, waypointProgress = 0;
    private int waypointIndex = 0;
    private boolean completed = false;
    private final List<Sample> samples = new ArrayList<>();
    private long lastGcCount = 0, lastGcTime = 0;
    private Camera camera;
    private World world;
    private ChunkManager chunkManager;
    
    public WorldBenchmark(String phase) {
        this.phase = phase;
        this.outputDir = "artifacts/world_bench/" + phase;
    }
    
    public void init(Camera cam, World w, ChunkManager cm) {
        this.camera = cam; this.world = w; this.chunkManager = cm;
        try { Files.createDirectories(Paths.get(outputDir)); } catch (IOException e) {}
        cam.getPosition().set(WAYPOINTS[0][0], WAYPOINTS[0][1], WAYPOINTS[0][2]);
        cam.setYaw(0); cam.setPitch(-10);
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            lastGcCount += gc.getCollectionCount(); lastGcTime += gc.getCollectionTime();
        }
        writeConfig();
        System.out.println("[WorldBench] Started " + phase);
    }
    
    public boolean update(float dt, float frameTime, float fps) {
        if (completed) return true;
        elapsedTime += dt; sampleTimer += dt;
        updatePath(dt);
        if (sampleTimer >= SAMPLE_INTERVAL) { sampleTimer -= SAMPLE_INTERVAL; collectSample(frameTime, fps); }
        if (elapsedTime >= DURATION_SECONDS) { completed = true; writeResults(); return true; }
        return false;
    }
    
    private void updatePath(float dt) {
        if (waypointIndex >= WAYPOINTS.length - 1) { waypointIndex = 0; waypointProgress = 0; }
        float[] c = WAYPOINTS[waypointIndex], n = WAYPOINTS[waypointIndex + 1];
        float dx = n[0]-c[0], dy = n[1]-c[1], dz = n[2]-c[2];
        float dist = (float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        waypointProgress += (CAMERA_SPEED * dt) / dist;
        if (waypointProgress >= 1.0f) { waypointProgress -= 1.0f; waypointIndex++; if (waypointIndex >= WAYPOINTS.length - 1) waypointIndex = 0; c = WAYPOINTS[waypointIndex]; n = WAYPOINTS[(waypointIndex+1)%WAYPOINTS.length]; }
        camera.getPosition().set(c[0]+(n[0]-c[0])*waypointProgress, c[1]+(n[1]-c[1])*waypointProgress, c[2]+(n[2]-c[2])*waypointProgress);
        camera.setYaw((float)Math.toDegrees(Math.atan2(n[0]-c[0], n[2]-c[2])));
    }
    
    private void collectSample(float ft, float fps) {
        Sample s = new Sample(); s.t = elapsedTime; s.fps = fps; s.ft = ft;
        MemoryUsage h = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage(); s.heap = h.getUsed()/(1024.0*1024.0);
        long gc = 0, gt = 0;
        for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) { gc += g.getCollectionCount(); gt += g.getCollectionTime(); }
        s.gcCount = gc - lastGcCount; s.gcTime = gt - lastGcTime; lastGcCount = gc; lastGcTime = gt;
        if (world != null) s.chunks = world.getChunkMap().size();
        if (chunkManager != null) { s.lod0 = chunkManager.getLod0Count(); s.lod1 = chunkManager.getLod1Count(); s.lod2 = chunkManager.getLod2Count(); s.lod3 = chunkManager.getLod3Count(); }
        if (camera != null) { s.x = camera.getPosition().x; s.y = camera.getPosition().y; s.z = camera.getPosition().z; }
        samples.add(s);
    }
    
    private void writeConfig() {
        try { Files.writeString(Paths.get(outputDir, "bench_config.json"), "{\"phase\":\""+phase+"\",\"seed\":"+FIXED_SEED+",\"duration\":"+DURATION_SECONDS+",\"timestamp\":\""+Instant.now()+"\"}"); } catch (IOException e) {}
    }
    
    private void writeResults() {
        try {
            StringBuilder sb = new StringBuilder(); sb.append("{\"samples\":[");
            for (int i = 0; i < samples.size(); i++) { Sample s = samples.get(i); sb.append("{\"t\":").append(s.t).append(",\"fps\":").append(s.fps).append(",\"heap\":").append(s.heap).append(",\"chunks\":").append(s.chunks).append("}"); if (i < samples.size()-1) sb.append(","); }
            sb.append("]}"); Files.writeString(Paths.get(outputDir, "perf_timeline.json"), sb.toString());
            double avgFps = samples.stream().mapToDouble(s -> s.fps).average().orElse(0);
            double maxHeap = samples.stream().mapToDouble(s -> s.heap).max().orElse(0);
            int maxChunks = samples.stream().mapToInt(s -> s.chunks).max().orElse(0);
            String sum = "Phase: "+phase+"\nAvg FPS: "+String.format("%.1f",avgFps)+"\nMax Heap: "+String.format("%.1f",maxHeap)+" MB\nMax Chunks: "+maxChunks+"\n";
            Files.writeString(Paths.get(outputDir, "summary.txt"), sum); System.out.println(sum);
        } catch (IOException e) {}
    }
    
    private static class Sample { float t, fps, ft, x, y, z; double heap; long gcCount, gcTime; int chunks, lod0, lod1, lod2, lod3; }
}
