package com.voxelgame.bench;

/**
 * Global toggles for benchmark fixes.
 * Set via --bench-fix KEY=true|false
 */
public class BenchFixes {
    
    // Fix A: Use primitive float[]/int[] builders instead of ArrayList<Float>/ArrayList<Integer>
    // ENABLED by default - reduces GC pressure significantly
    public static boolean FIX_MESH_PRIMITIVE_BUFFERS = true;

    // Fix B: Use packed long keys instead of new ChunkPos() per lookup (DEPRECATED - use B2)
    public static boolean FIX_CHUNKPOS_NO_ALLOC = true;

    // Fix B2: Use fastutil Long2ObjectOpenHashMap with RW lock (no boxing at all)
    // ENABLED by default - eliminates boxing overhead in chunk lookups
    public static boolean FIX_B2_PRIMITIVE_MAP = true;

    // Fix B3: Snapshot-based meshing (resolve neighbors once, then pure array access)
    // ENABLED by default - zero map lookups in meshing hot path
    public static boolean FIX_B3_SNAPSHOT_MESH = true;

    // Fix B3.1: Move snapshot creation off main thread (worker resolves neighbors at job start)
    // ENABLED by default - removes main thread latency for neighbor resolution
    public static boolean FIX_B31_SNAPSHOT_OFFTHREAD = true;

    // Fix C: Async region IO instead of sync writes on main thread
    // ENABLED by default - non-blocking chunk saves
    public static boolean FIX_ASYNC_REGION_IO = true;

    // Fix C1 (V2): Bounded backlog + stronger coalescing + adaptive save rate
    // ENABLED by default - prevents IO queue explosion
    public static boolean FIX_ASYNC_REGION_IO_V2 = true;

    // Fix D: Section-based meshing (skip empty 16×16×16 sections)
    // DISABLED - draw call multiplication outweighs vertex reduction benefit
    // TODO: Batch adjacent sections into single meshes before re-enabling
    public static boolean FIX_SECTION_MESHING = false;

    /**
     * Parse --bench-fix argument: KEY=true|false
     */
    public static void parse(String arg) {
        String[] parts = arg.split("=", 2);
        if (parts.length != 2) return;
        
        String key = parts[0].trim();
        boolean value = Boolean.parseBoolean(parts[1].trim());
        
        switch (key) {
            case "FIX_MESH_PRIMITIVE_BUFFERS" -> FIX_MESH_PRIMITIVE_BUFFERS = value;
            case "FIX_CHUNKPOS_NO_ALLOC" -> FIX_CHUNKPOS_NO_ALLOC = value;
            case "FIX_B2_PRIMITIVE_MAP" -> FIX_B2_PRIMITIVE_MAP = value;
            case "FIX_B3_SNAPSHOT_MESH" -> FIX_B3_SNAPSHOT_MESH = value;
            case "FIX_B31_SNAPSHOT_OFFTHREAD" -> FIX_B31_SNAPSHOT_OFFTHREAD = value;
            case "FIX_ASYNC_REGION_IO" -> FIX_ASYNC_REGION_IO = value;
            case "FIX_ASYNC_REGION_IO_V2" -> FIX_ASYNC_REGION_IO_V2 = value;
            case "FIX_SECTION_MESHING" -> FIX_SECTION_MESHING = value;
        }
    }
    
    public static String status() {
        return String.format(
            "FIX_MESH_PRIMITIVE_BUFFERS=%s, FIX_B3_SNAPSHOT_MESH=%s, FIX_B31_SNAPSHOT_OFFTHREAD=%s, FIX_ASYNC_REGION_IO=%s, FIX_ASYNC_REGION_IO_V2=%s, FIX_SECTION_MESHING=%s",
            FIX_MESH_PRIMITIVE_BUFFERS, FIX_B3_SNAPSHOT_MESH, FIX_B31_SNAPSHOT_OFFTHREAD, FIX_ASYNC_REGION_IO, FIX_ASYNC_REGION_IO_V2, FIX_SECTION_MESHING);
    }
}
