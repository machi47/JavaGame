package com.voxelgame.world.mesh;

import com.voxelgame.bench.BenchFixes;
import com.voxelgame.render.TextureAtlas;
import com.voxelgame.world.*;
import com.voxelgame.world.lighting.ProbeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * MINIMAL BASELINE MESHER - Optimized for performance.
 *
 * Vertex format: [x, y, z, u, v, light] = 6 floats per vertex
 * - light: combined (skyVisibility * AO * faceLight) baked into single value
 *
 * Removed for performance:
 * - RGB block light (use scalar)
 * - Horizon weight
 * - Indirect lighting probes
 * - HeightfieldVisibility ray tracing
 *
 * Simple lighting model:
 *   finalColor = textureColor * vLight
 */
public class NaiveMesher implements Mesher {

    // ============== BUFFER POOLING ==============
    // ThreadLocal buffers to eliminate GC pressure from per-mesh allocations.
    // Each mesh thread gets its own instance, no synchronization needed.
    // Sized for worst-case chunk (rarely needs to grow).
    private static final ThreadLocal<FloatArrayBuilder> OPAQUE_VERTS_POOL =
        ThreadLocal.withInitial(() -> new FloatArrayBuilder(65536));
    private static final ThreadLocal<IntArrayBuilder> OPAQUE_INDICES_POOL =
        ThreadLocal.withInitial(() -> new IntArrayBuilder(32768));
    private static final ThreadLocal<FloatArrayBuilder> TRANS_VERTS_POOL =
        ThreadLocal.withInitial(() -> new FloatArrayBuilder(16384));
    private static final ThreadLocal<IntArrayBuilder> TRANS_INDICES_POOL =
        ThreadLocal.withInitial(() -> new IntArrayBuilder(8192));

    // Face indices: 0=top(+Y), 1=bottom(-Y), 2=north(-Z), 3=south(+Z), 4=east(+X), 5=west(-X)
    // Wider spread between faces gives stronger directional depth cues
    private static final float[] FACE_LIGHT = {1.0f, 0.45f, 0.7f, 0.7f, 0.8f, 0.6f};

    // Direction offsets for neighbor checking [dx, dy, dz]
    private static final int[][] FACE_NORMALS = {
        { 0,  1,  0}, // top
        { 0, -1,  0}, // bottom
        { 0,  0, -1}, // north
        { 0,  0,  1}, // south
        { 1,  0,  0}, // east
        {-1,  0,  0}, // west
    };

    // AO levels: 0 occluders = 1.0, 1 = 0.50, 2 = 0.22, 3 = 0.08
    // Tuned for stronger terrain contrast (less flat-looking surfaces).
    private static final float[] AO_LEVELS = {1.0f, 0.50f, 0.22f, 0.08f};

    /**
     * For each face, the 4 vertices and their 3 AO neighbor offsets.
     * Offsets are in LOCAL face space relative to the block position.
     * [vertex][neighbor_index][dx, dy, dz]
     *
     * The 3 neighbors for AO at each vertex are:
     *   side1, side2, and the corner (diagonally opposite along the face plane).
     */

    // Top face (+Y): y+1 plane, vertices at corners of (x, x+1) × (z, z+1)
    // Vertex 0: (x, y+1, z)      — AO checks: (-1,1,0), (0,1,-1), (-1,1,-1)
    // Vertex 1: (x, y+1, z+1)    — AO checks: (-1,1,0), (0,1,+1), (-1,1,+1)
    // Vertex 2: (x+1, y+1, z+1)  — AO checks: (+1,1,0), (0,1,+1), (+1,1,+1)
    // Vertex 3: (x+1, y+1, z)    — AO checks: (+1,1,0), (0,1,-1), (+1,1,-1)
    private static final int[][][] AO_TOP = {
        {{ -1, 1, 0 }, { 0, 1, -1 }, { -1, 1, -1 }},
        {{ -1, 1, 0 }, { 0, 1,  1 }, { -1, 1,  1 }},
        {{  1, 1, 0 }, { 0, 1,  1 }, {  1, 1,  1 }},
        {{  1, 1, 0 }, { 0, 1, -1 }, {  1, 1, -1 }},
    };

    // Bottom face (-Y): y plane
    // Vertex 0: (x, y, z+1)
    // Vertex 1: (x, y, z)
    // Vertex 2: (x+1, y, z)
    // Vertex 3: (x+1, y, z+1)
    private static final int[][][] AO_BOTTOM = {
        {{ -1, -1, 0 }, { 0, -1,  1 }, { -1, -1,  1 }},
        {{ -1, -1, 0 }, { 0, -1, -1 }, { -1, -1, -1 }},
        {{  1, -1, 0 }, { 0, -1, -1 }, {  1, -1, -1 }},
        {{  1, -1, 0 }, { 0, -1,  1 }, {  1, -1,  1 }},
    };

    // North face (-Z): z plane
    // Vertex 0: (x+1, y+1, z)
    // Vertex 1: (x+1, y, z)
    // Vertex 2: (x, y, z)
    // Vertex 3: (x, y+1, z)
    private static final int[][][] AO_NORTH = {
        {{  1, 0, -1 }, { 0, 1, -1 }, {  1, 1, -1 }},
        {{  1, 0, -1 }, { 0, -1, -1 }, {  1, -1, -1 }},
        {{ -1, 0, -1 }, { 0, -1, -1 }, { -1, -1, -1 }},
        {{ -1, 0, -1 }, { 0, 1, -1 }, { -1, 1, -1 }},
    };

    // South face (+Z): z+1 plane
    // Vertex 0: (x, y+1, z+1)
    // Vertex 1: (x, y, z+1)
    // Vertex 2: (x+1, y, z+1)
    // Vertex 3: (x+1, y+1, z+1)
    private static final int[][][] AO_SOUTH = {
        {{ -1, 0, 1 }, { 0, 1, 1 }, { -1, 1, 1 }},
        {{ -1, 0, 1 }, { 0, -1, 1 }, { -1, -1, 1 }},
        {{  1, 0, 1 }, { 0, -1, 1 }, {  1, -1, 1 }},
        {{  1, 0, 1 }, { 0, 1, 1 }, {  1, 1, 1 }},
    };

    // East face (+X): x+1 plane
    // Vertex 0: (x+1, y+1, z+1)
    // Vertex 1: (x+1, y, z+1)
    // Vertex 2: (x+1, y, z)
    // Vertex 3: (x+1, y+1, z)
    private static final int[][][] AO_EAST = {
        {{ 1, 0,  1 }, { 1, 1, 0 }, { 1, 1,  1 }},
        {{ 1, 0,  1 }, { 1, -1, 0 }, { 1, -1,  1 }},
        {{ 1, 0, -1 }, { 1, -1, 0 }, { 1, -1, -1 }},
        {{ 1, 0, -1 }, { 1, 1, 0 }, { 1, 1, -1 }},
    };

    // West face (-X): x plane
    // Vertex 0: (x, y+1, z)
    // Vertex 1: (x, y, z)
    // Vertex 2: (x, y, z+1)
    // Vertex 3: (x, y+1, z+1)
    private static final int[][][] AO_WEST = {
        {{ -1, 0, -1 }, { -1, 1, 0 }, { -1, 1, -1 }},
        {{ -1, 0, -1 }, { -1, -1, 0 }, { -1, -1, -1 }},
        {{ -1, 0,  1 }, { -1, -1, 0 }, { -1, -1,  1 }},
        {{ -1, 0,  1 }, { -1, 1, 0 }, { -1, 1,  1 }},
    };

    private static final int[][][][] AO_OFFSETS = { AO_TOP, AO_BOTTOM, AO_NORTH, AO_SOUTH, AO_EAST, AO_WEST };

    /** Probe manager for indirect lighting (may be null if not yet initialized) */
    private static ProbeManager probeManager;
    
    /** Set the probe manager for indirect lighting sampling. */
    public static void setProbeManager(ProbeManager pm) {
        probeManager = pm;
    }

    /**
     * For each face, the 4 vertex positions relative to block origin.
     * Format: [face][vertex][x_off, y_off, z_off]
     */
    private static final float[][][] FACE_VERTICES = {
        // Top (+Y)
        {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
        // Bottom (-Y)
        {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
        // North (-Z)
        {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
        // South (+Z)
        {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
        // East (+X)
        {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}},
        // West (-X)
        {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
    };

    /**
     * UV coordinates for each vertex of each face.
     * Format: [face][vertex][u_idx, v_idx] where 0 = min, 1 = max
     */
    private static final int[][][] FACE_UV = {
        // Top
        {{0, 0}, {0, 1}, {1, 1}, {1, 0}},
        // Bottom
        {{0, 0}, {0, 1}, {1, 1}, {1, 0}},
        // North
        {{0, 0}, {0, 1}, {1, 1}, {1, 0}},
        // South
        {{0, 0}, {0, 1}, {1, 1}, {1, 0}},
        // East
        {{0, 0}, {0, 1}, {1, 1}, {1, 0}},
        // West
        {{0, 0}, {0, 1}, {1, 1}, {1, 0}},
    };

    private TextureAtlas atlas;

    public NaiveMesher(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    @Override
    public RawMeshResult meshAllRaw(Chunk chunk, WorldAccess world) {
        // Dispatch to primitive buffer version if toggle enabled
        if (BenchFixes.FIX_MESH_PRIMITIVE_BUFFERS) {
            return meshAllRawPrimitive(chunk, world);
        }
        
        // Separate lists for opaque and transparent geometry
        List<Float> opaqueVerts = new ArrayList<>();
        List<Integer> opaqueIndices = new ArrayList<>();
        int opaqueVertexCount = 0;

        List<Float> transVerts = new ArrayList<>();
        List<Integer> transIndices = new ArrayList<>();
        int transVertexCount = 0;

        ChunkPos pos = chunk.getPos();
        int cx = pos.x() * WorldConstants.CHUNK_SIZE;
        int cz = pos.z() * WorldConstants.CHUNK_SIZE;

        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int y = 0; y < WorldConstants.WORLD_HEIGHT; y++) {
                for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                    int blockId = chunk.getBlock(x, y, z);
                    if (blockId == 0) continue; // Skip air

                    Block block = Blocks.get(blockId);
                    if (!block.solid() && !block.transparent()) continue;

                    // Determine if this block is transparent (water, etc.)
                    boolean isTransparent = block.transparent() && !block.solid();

                    // Choose the target vertex/index lists
                    List<Float> verts = isTransparent ? transVerts : opaqueVerts;
                    List<Integer> indices = isTransparent ? transIndices : opaqueIndices;

                    float wx = cx + x;
                    float wy = y;
                    float wz = cz + z;

                    // Special rendering for torches: small centered cube
                    // Rendered in OPAQUE pass with alpha-discard (shader discards alpha < 0.1)
                    // This prevents the transparent pass's uAlpha/depth-write-off from making torches see-through
                    if (blockId == Blocks.TORCH.id() || blockId == Blocks.REDSTONE_TORCH.id()) {
                        opaqueVertexCount = meshTorch(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                       wx, wy, wz, block, atlas);
                        continue;
                    }

                    // Special rendering for flowers: cross-shaped billboard
                    // Rendered in OPAQUE pass with alpha-discard
                    if (Blocks.isFlower(blockId)) {
                        opaqueVertexCount = meshCross(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                       wx, wy, wz, block, atlas);
                        continue;
                    }

                    // Special rendering for wheat crops: cross-shaped billboard (like flowers)
                    if (Blocks.isWheatCrop(blockId)) {
                        opaqueVertexCount = meshCross(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                       wx, wy, wz, block, atlas);
                        continue;
                    }

                    // Special rendering for rails: flat quad on ground
                    // Rendered in OPAQUE pass with alpha-discard
                    if (Blocks.isRail(blockId)) {
                        opaqueVertexCount = meshFlatQuad(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                          wx, wy, wz, block, atlas);
                        continue;
                    }

                    // Special rendering for redstone wire: flat quad on ground
                    // Rendered in OPAQUE pass with alpha-discard
                    if (blockId == Blocks.REDSTONE_WIRE.id()) {
                        opaqueVertexCount = meshFlatQuad(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                          wx, wy, wz, block, atlas);
                        continue;
                    }

                    // Special rendering for redstone repeater: flat slab on ground
                    if (blockId == Blocks.REDSTONE_REPEATER.id()) {
                        transVertexCount = meshFlatSlab(transVerts, transIndices, transVertexCount,
                                                         wx, wy, wz, block, atlas);
                        continue;
                    }

                    for (int face = 0; face < 6; face++) {
                        int nx = x + FACE_NORMALS[face][0];
                        int ny = y + FACE_NORMALS[face][1];
                        int nz = z + FACE_NORMALS[face][2];

                        int neighborId;
                        boolean isCrossChunk = false;
                        if (nx >= 0 && nx < WorldConstants.CHUNK_SIZE &&
                            ny >= 0 && ny < WorldConstants.WORLD_HEIGHT &&
                            nz >= 0 && nz < WorldConstants.CHUNK_SIZE) {
                            neighborId = chunk.getBlock(nx, ny, nz);
                        } else {
                            isCrossChunk = true;
                            neighborId = world.getBlock(cx + nx, ny, cz + nz);
                        }

                        // WATER SEAM FIX: If transparent block at chunk boundary sees air,
                        // the neighbor chunk likely isn't loaded. Skip this face - it will
                        // render correctly when neighbor loads and triggers remesh.
                        if (isCrossChunk && isTransparent && neighborId == 0) {
                            continue;
                        }

                        Block neighbor = Blocks.get(neighborId);
                        if (neighbor.solid() && !neighbor.transparent()) continue;
                        if (blockId == neighborId && neighbor.transparent()) continue;

                        int texIdx = block.getTextureIndex(face);
                        float[] uv = atlas.getUV(texIdx);
                        float dirLight = FACE_LIGHT[face];

                        // Compute per-vertex AO and separate sky/block light
                        int[][][] aoOffsets = AO_OFFSETS[face];
                        float[] vertSkyLight = new float[4];
                        float[][] vertBlockLightRGB = new float[4][3];  // Phase 4: RGB per vertex
                        int[] aoValues = new int[4];

                        // Compute per-vertex visibility using HeightfieldVisibility
                        float[] vertHorizonWeight = new float[4];
                        
                        for (int v = 0; v < 4; v++) {
                            boolean side1 = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][0]);
                            boolean side2 = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][1]);
                            boolean corner = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][2]);

                            int ao;
                            if (side1 && side2) {
                                ao = 3;
                            } else {
                                ao = (side1 ? 1 : 0) + (side2 ? 1 : 0) + (corner ? 1 : 0);
                            }
                            aoValues[v] = ao;

                            float aoFactor = AO_LEVELS[ao];
                            
                            // Sample visibility using HeightfieldVisibility for zenith/horizon split
                            int sampleX = cx + x + FACE_NORMALS[face][0];
                            int sampleY = y + FACE_NORMALS[face][1];
                            int sampleZ = cz + z + FACE_NORMALS[face][2];
                            
                            float existingVis = getSkyVisibilitySafe(world, sampleX, sampleY, sampleZ);
                            HeightfieldVisibility.VisibilityResult visResult = 
                                HeightfieldVisibility.computeWithHint(world, sampleX, sampleY, sampleZ, existingVis);
                            
                            float skyVis = visResult.visibility;
                            vertHorizonWeight[v] = visResult.horizonWeight;
                            
                            // Phase 4: Sample RGB block light
                            float[] blockLightRGB = sampleVertexBlockLightRGB(world, cx + x, y, cz + z,
                                                                               face, aoOffsets[v]);

                            // Pass visibility and block light with AO and directional factors baked in
                            // Shader computes actual RGB from these + time-of-day uniforms
                            vertSkyLight[v] = dirLight * aoFactor * skyVis;
                            vertBlockLightRGB[v][0] = dirLight * aoFactor * blockLightRGB[0];
                            vertBlockLightRGB[v][1] = dirLight * aoFactor * blockLightRGB[1];
                            vertBlockLightRGB[v][2] = dirLight * aoFactor * blockLightRGB[2];
                        }

                        float[][] fv = FACE_VERTICES[face];
                        int[][] fuv = FACE_UV[face];

                        // Sample indirect lighting for this face (sample at face center)
                        float faceX = wx + 0.5f + FACE_NORMALS[face][0] * 0.5f;
                        float faceY = wy + 0.5f + FACE_NORMALS[face][1] * 0.5f;
                        float faceZ = wz + 0.5f + FACE_NORMALS[face][2] * 0.5f;
                        float[] indirect = sampleIndirect(faceX, faceY, faceZ);

                        int baseVertex = isTransparent ? transVertexCount : opaqueVertexCount;
                        for (int v = 0; v < 4; v++) {
                            float u = (fuv[v][0] == 0) ? uv[0] : uv[2];
                            float vCoord = (fuv[v][1] == 0) ? uv[1] : uv[3];

                            float vx = wx + fv[v][0];
                            float vy = wy + fv[v][1];
                            float vz = wz + fv[v][2];

                            // Lower water surface slightly to avoid perfectly-flat full block tops.
                            // This improves readability and reduces the opaque-sheet look.
                            if (isTransparent && Blocks.isWater(blockId) && fv[v][1] > 0.5f) {
                                vy -= 0.12f;
                            }

                            addVertex(verts, vx, vy, vz,
                                     u, vCoord, vertSkyLight[v], 
                                     vertBlockLightRGB[v][0], vertBlockLightRGB[v][1], vertBlockLightRGB[v][2],
                                     vertHorizonWeight[v],
                                     indirect[0], indirect[1], indirect[2]);
                        }
                        if (isTransparent) {
                            transVertexCount += 4;
                        } else {
                            opaqueVertexCount += 4;
                        }

                        // AO-aware quad splitting
                        if (aoValues[0] + aoValues[2] > aoValues[1] + aoValues[3]) {
                            indices.add(baseVertex + 1);
                            indices.add(baseVertex + 2);
                            indices.add(baseVertex + 3);
                            indices.add(baseVertex + 1);
                            indices.add(baseVertex + 3);
                            indices.add(baseVertex);
                        } else {
                            indices.add(baseVertex);
                            indices.add(baseVertex + 1);
                            indices.add(baseVertex + 2);
                            indices.add(baseVertex);
                            indices.add(baseVertex + 2);
                            indices.add(baseVertex + 3);
                        }
                    }
                }
            }
        }

        MeshData opaqueData = buildMeshData(opaqueVerts, opaqueIndices);
        MeshData transparentData = buildMeshData(transVerts, transIndices);
        return new RawMeshResult(opaqueData, transparentData);
    }

    @Override
    public MeshResult meshAll(Chunk chunk, WorldAccess world) {
        // meshAll delegates to raw + upload (for main-thread callers)
        RawMeshResult raw = meshAllRaw(chunk, world);
        return raw.upload();
    }

    private MeshData buildMeshData(List<Float> verts, List<Integer> indices) {
        float[] vertArray = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) {
            vertArray[i] = verts.get(i);
        }
        int[] idxArray = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            idxArray[i] = indices.get(i);
        }
        return new MeshData(vertArray, idxArray);
    }
    
    // =============== PRIMITIVE BUFFER VERSION (Fix A) ===============
    
    /**
     * Primitive buffer version of meshAllRaw - zero boxing overhead.
     * Uses FloatArrayBuilder/IntArrayBuilder instead of ArrayList<Float>/ArrayList<Integer>.
     */
    private RawMeshResult meshAllRawPrimitive(Chunk chunk, WorldAccess world) {
        // Get pooled buffers and clear for reuse (eliminates GC pressure)
        FloatArrayBuilder opaqueVerts = OPAQUE_VERTS_POOL.get();
        IntArrayBuilder opaqueIndices = OPAQUE_INDICES_POOL.get();
        FloatArrayBuilder transVerts = TRANS_VERTS_POOL.get();
        IntArrayBuilder transIndices = TRANS_INDICES_POOL.get();
        opaqueVerts.clear();
        opaqueIndices.clear();
        transVerts.clear();
        transIndices.clear();
        int opaqueVertexCount = 0;
        int transVertexCount = 0;

        ChunkPos pos = chunk.getPos();
        int cx = pos.x() * WorldConstants.CHUNK_SIZE;
        int cz = pos.z() * WorldConstants.CHUNK_SIZE;

        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int y = 0; y < WorldConstants.WORLD_HEIGHT; y++) {
                for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                    int blockId = chunk.getBlock(x, y, z);
                    if (blockId == 0) continue;

                    Block block = Blocks.get(blockId);
                    if (!block.solid() && !block.transparent()) continue;

                    boolean isTransparent = block.transparent() && !block.solid();
                    FloatArrayBuilder verts = isTransparent ? transVerts : opaqueVerts;
                    IntArrayBuilder indices = isTransparent ? transIndices : opaqueIndices;

                    float wx = cx + x;
                    float wy = y;
                    float wz = cz + z;

                    if (blockId == Blocks.TORCH.id() || blockId == Blocks.REDSTONE_TORCH.id()) {
                        opaqueVertexCount = meshTorchPrimitive(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                                wx, wy, wz, block, atlas);
                        continue;
                    }

                    if (Blocks.isFlower(blockId)) {
                        opaqueVertexCount = meshCrossPrimitive(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                                wx, wy, wz, block, atlas);
                        continue;
                    }

                    if (Blocks.isWheatCrop(blockId)) {
                        opaqueVertexCount = meshCrossPrimitive(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                                wx, wy, wz, block, atlas);
                        continue;
                    }

                    if (Blocks.isRail(blockId)) {
                        opaqueVertexCount = meshFlatQuadPrimitive(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                                   wx, wy, wz, block, atlas);
                        continue;
                    }

                    if (blockId == Blocks.REDSTONE_WIRE.id()) {
                        opaqueVertexCount = meshFlatQuadPrimitive(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                                   wx, wy, wz, block, atlas);
                        continue;
                    }

                    if (blockId == Blocks.REDSTONE_REPEATER.id()) {
                        transVertexCount = meshFlatSlabPrimitive(transVerts, transIndices, transVertexCount,
                                                                  wx, wy, wz, block, atlas);
                        continue;
                    }

                    for (int face = 0; face < 6; face++) {
                        int nx = x + FACE_NORMALS[face][0];
                        int ny = y + FACE_NORMALS[face][1];
                        int nz = z + FACE_NORMALS[face][2];

                        int neighborId;
                        boolean isCrossChunk = false;
                        if (nx >= 0 && nx < WorldConstants.CHUNK_SIZE &&
                            ny >= 0 && ny < WorldConstants.WORLD_HEIGHT &&
                            nz >= 0 && nz < WorldConstants.CHUNK_SIZE) {
                            neighborId = chunk.getBlock(nx, ny, nz);
                        } else {
                            isCrossChunk = true;
                            neighborId = world.getBlock(cx + nx, ny, cz + nz);
                        }

                        // WATER SEAM FIX: If transparent block at chunk boundary sees air,
                        // the neighbor chunk likely isn't loaded. Skip this face - it will
                        // render correctly when neighbor loads and triggers remesh.
                        if (isCrossChunk && isTransparent && neighborId == 0) {
                            continue;
                        }

                        Block neighbor = Blocks.get(neighborId);
                        if (neighbor.solid() && !neighbor.transparent()) continue;
                        if (blockId == neighborId && neighbor.transparent()) continue;

                        int texIdx = block.getTextureIndex(face);
                        float[] uv = atlas.getUV(texIdx);
                        float dirLight = FACE_LIGHT[face];

                        // MINIMAL BASELINE: Simple AO + sky visibility, no RGB light, no indirect
                        int[][][] aoOffsets = AO_OFFSETS[face];
                        float[] vertLight = new float[4];
                        int[] aoValues = new int[4];

                        // Sample sky visibility once for the face (not per-vertex)
                        int sampleX = cx + x + FACE_NORMALS[face][0];
                        int sampleY = y + FACE_NORMALS[face][1];
                        int sampleZ = cz + z + FACE_NORMALS[face][2];
                        float skyVis = getSkyVisibilitySafe(world, sampleX, sampleY, sampleZ);

                        for (int v = 0; v < 4; v++) {
                            boolean side1 = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][0]);
                            boolean side2 = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][1]);
                            boolean corner = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][2]);

                            int ao = (side1 && side2) ? 3 : ((side1 ? 1 : 0) + (side2 ? 1 : 0) + (corner ? 1 : 0));
                            aoValues[v] = ao;
                            float aoFactor = AO_LEVELS[ao];

                            // Simple combined light: faceLight * AO * skyVisibility
                            // Add small ambient to prevent pure black in caves
                            vertLight[v] = dirLight * aoFactor * Math.max(skyVis, 0.15f);
                        }

                        float[][] fv = FACE_VERTICES[face];
                        int[][] fuv = FACE_UV[face];

                        int baseVertex = isTransparent ? transVertexCount : opaqueVertexCount;
                        for (int v = 0; v < 4; v++) {
                            float u = (fuv[v][0] == 0) ? uv[0] : uv[2];
                            float vCoord = (fuv[v][1] == 0) ? uv[1] : uv[3];

                            float vx = wx + fv[v][0];
                            float vy = wy + fv[v][1];
                            float vz = wz + fv[v][2];

                            if (isTransparent && Blocks.isWater(blockId) && fv[v][1] > 0.5f) {
                                vy -= 0.12f;
                            }

                            // 6-float vertex: x, y, z, u, v, light
                            addVertexMinimal(verts, vx, vy, vz, u, vCoord, vertLight[v]);
                        }
                        if (isTransparent) {
                            transVertexCount += 4;
                        } else {
                            opaqueVertexCount += 4;
                        }

                        // AO-aware quad splitting
                        if (aoValues[0] + aoValues[2] > aoValues[1] + aoValues[3]) {
                            indices.add(baseVertex + 1, baseVertex + 2, baseVertex + 3);
                            indices.add(baseVertex + 1, baseVertex + 3, baseVertex);
                        } else {
                            indices.add(baseVertex, baseVertex + 1, baseVertex + 2);
                            indices.add(baseVertex, baseVertex + 2, baseVertex + 3);
                        }
                    }
                }
            }
        }

        MeshData opaqueData = new MeshData(opaqueVerts.toArray(), opaqueIndices.toArray());
        MeshData transparentData = new MeshData(transVerts.toArray(), transIndices.toArray());
        return new RawMeshResult(opaqueData, transparentData);
    }

    // =============== SECTION-BASED MESHING ===============

    /**
     * Mesh all sections of a chunk, skipping empty sections.
     * This is the optimized path that avoids meshing air-only sections (sky)
     * and fully-solid sections (deep underground with no exposed faces).
     *
     * @param chunk The chunk to mesh
     * @param world World access for cross-chunk lookups
     * @return RawSectionMeshResult with per-section mesh data
     */
    public RawSectionMeshResult meshAllSectionsRaw(Chunk chunk, WorldAccess world) {
        RawSectionMeshResult result = new RawSectionMeshResult();

        ChunkPos pos = chunk.getPos();
        int cx = pos.x() * WorldConstants.CHUNK_SIZE;
        int cz = pos.z() * WorldConstants.CHUNK_SIZE;

        for (int section = 0; section < WorldConstants.SECTIONS_PER_CHUNK; section++) {
            // Check section flag - skip empty sections
            byte flag = chunk.getSectionFlag(section);
            if (flag == Chunk.SECTION_EMPTY) {
                continue; // No mesh needed for all-air section
            }

            // Mesh this section
            MeshData[] sectionData = meshSectionPrimitive(chunk, world, cx, cz, section);
            if (sectionData[0] != null || sectionData[1] != null) {
                result.setSection(section, sectionData[0], sectionData[1]);
            }
        }

        return result;
    }

    /**
     * Mesh a single 16×16×16 section of a chunk.
     *
     * @return [opaqueData, transparentData] - either may be null if empty
     */
    private MeshData[] meshSectionPrimitive(Chunk chunk, WorldAccess world, int cx, int cz, int sectionIndex) {
        // Get pooled buffers and clear for reuse
        FloatArrayBuilder opaqueVerts = OPAQUE_VERTS_POOL.get();
        IntArrayBuilder opaqueIndices = OPAQUE_INDICES_POOL.get();
        FloatArrayBuilder transVerts = TRANS_VERTS_POOL.get();
        IntArrayBuilder transIndices = TRANS_INDICES_POOL.get();
        opaqueVerts.clear();
        opaqueIndices.clear();
        transVerts.clear();
        transIndices.clear();
        int opaqueVertexCount = 0;
        int transVertexCount = 0;

        int yStart = sectionIndex * WorldConstants.SECTION_HEIGHT;
        int yEnd = yStart + WorldConstants.SECTION_HEIGHT;

        for (int x = 0; x < WorldConstants.CHUNK_SIZE; x++) {
            for (int y = yStart; y < yEnd; y++) {
                for (int z = 0; z < WorldConstants.CHUNK_SIZE; z++) {
                    int blockId = chunk.getBlock(x, y, z);
                    if (blockId == 0) continue;

                    Block block = Blocks.get(blockId);
                    if (!block.solid() && !block.transparent()) continue;

                    boolean isTransparent = block.transparent() && !block.solid();
                    FloatArrayBuilder verts = isTransparent ? transVerts : opaqueVerts;
                    IntArrayBuilder indices = isTransparent ? transIndices : opaqueIndices;

                    float wx = cx + x;
                    float wy = y;
                    float wz = cz + z;

                    // Special block handling (torches, flowers, etc.)
                    if (blockId == Blocks.TORCH.id() || blockId == Blocks.REDSTONE_TORCH.id()) {
                        opaqueVertexCount = meshTorchPrimitive(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                                wx, wy, wz, block, atlas);
                        continue;
                    }

                    if (Blocks.isFlower(blockId)) {
                        opaqueVertexCount = meshCrossPrimitive(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                                wx, wy, wz, block, atlas);
                        continue;
                    }

                    if (Blocks.isWheatCrop(blockId)) {
                        opaqueVertexCount = meshCrossPrimitive(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                                wx, wy, wz, block, atlas);
                        continue;
                    }

                    if (Blocks.isRail(blockId)) {
                        opaqueVertexCount = meshFlatQuadPrimitive(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                                   wx, wy, wz, block, atlas);
                        continue;
                    }

                    if (blockId == Blocks.REDSTONE_WIRE.id()) {
                        opaqueVertexCount = meshFlatQuadPrimitive(opaqueVerts, opaqueIndices, opaqueVertexCount,
                                                                   wx, wy, wz, block, atlas);
                        continue;
                    }

                    if (blockId == Blocks.REDSTONE_REPEATER.id()) {
                        transVertexCount = meshFlatSlabPrimitive(transVerts, transIndices, transVertexCount,
                                                                  wx, wy, wz, block, atlas);
                        continue;
                    }

                    // Standard block face meshing
                    for (int face = 0; face < 6; face++) {
                        int nx = x + FACE_NORMALS[face][0];
                        int ny = y + FACE_NORMALS[face][1];
                        int nz = z + FACE_NORMALS[face][2];

                        int neighborId;
                        boolean isCrossChunk = false;
                        if (nx >= 0 && nx < WorldConstants.CHUNK_SIZE &&
                            ny >= 0 && ny < WorldConstants.WORLD_HEIGHT &&
                            nz >= 0 && nz < WorldConstants.CHUNK_SIZE) {
                            neighborId = chunk.getBlock(nx, ny, nz);
                        } else {
                            isCrossChunk = true;
                            neighborId = world.getBlock(cx + nx, ny, cz + nz);
                        }

                        // WATER SEAM FIX: Skip faces toward unloaded chunks
                        if (isCrossChunk && isTransparent && neighborId == 0) {
                            continue;
                        }

                        Block neighbor = Blocks.get(neighborId);
                        if (neighbor.solid() && !neighbor.transparent()) continue;
                        if (blockId == neighborId && neighbor.transparent()) continue;

                        int texIdx = block.getTextureIndex(face);
                        float[] uv = atlas.getUV(texIdx);
                        float dirLight = FACE_LIGHT[face];

                        int[][][] aoOffsets = AO_OFFSETS[face];
                        float[] vertSkyLight = new float[4];
                        float[][] vertBlockLightRGB = new float[4][3];
                        int[] aoValues = new int[4];
                        float[] vertHorizonWeight = new float[4];

                        for (int v = 0; v < 4; v++) {
                            boolean side1 = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][0]);
                            boolean side2 = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][1]);
                            boolean corner = isOccluder(world, cx + x, y, cz + z, aoOffsets[v][2]);

                            int ao;
                            if (side1 && side2) {
                                ao = 3;
                            } else {
                                ao = (side1 ? 1 : 0) + (side2 ? 1 : 0) + (corner ? 1 : 0);
                            }
                            aoValues[v] = ao;

                            float aoFactor = AO_LEVELS[ao];

                            int sampleX = cx + x + FACE_NORMALS[face][0];
                            int sampleY = y + FACE_NORMALS[face][1];
                            int sampleZ = cz + z + FACE_NORMALS[face][2];

                            float existingVis = getSkyVisibilitySafe(world, sampleX, sampleY, sampleZ);
                            HeightfieldVisibility.VisibilityResult visResult =
                                HeightfieldVisibility.computeWithHint(world, sampleX, sampleY, sampleZ, existingVis);

                            float skyVis = visResult.visibility;
                            vertHorizonWeight[v] = visResult.horizonWeight;

                            float[] blockLightRGB = sampleVertexBlockLightRGB(world, cx + x, y, cz + z,
                                                                               face, aoOffsets[v]);

                            vertSkyLight[v] = dirLight * aoFactor * skyVis;
                            vertBlockLightRGB[v][0] = dirLight * aoFactor * blockLightRGB[0];
                            vertBlockLightRGB[v][1] = dirLight * aoFactor * blockLightRGB[1];
                            vertBlockLightRGB[v][2] = dirLight * aoFactor * blockLightRGB[2];
                        }

                        float[][] fv = FACE_VERTICES[face];
                        int[][] fuv = FACE_UV[face];

                        float faceX = wx + 0.5f + FACE_NORMALS[face][0] * 0.5f;
                        float faceY = wy + 0.5f + FACE_NORMALS[face][1] * 0.5f;
                        float faceZ = wz + 0.5f + FACE_NORMALS[face][2] * 0.5f;
                        float[] indirect = sampleIndirect(faceX, faceY, faceZ);

                        int baseVertex = isTransparent ? transVertexCount : opaqueVertexCount;
                        for (int v = 0; v < 4; v++) {
                            float u = (fuv[v][0] == 0) ? uv[0] : uv[2];
                            float vCoord = (fuv[v][1] == 0) ? uv[1] : uv[3];

                            float vx = wx + fv[v][0];
                            float vy = wy + fv[v][1];
                            float vz = wz + fv[v][2];

                            if (isTransparent && Blocks.isWater(blockId) && fv[v][1] > 0.5f) {
                                vy -= 0.12f;
                            }

                            addVertexPrimitive(verts, vx, vy, vz,
                                              u, vCoord, vertSkyLight[v],
                                              vertBlockLightRGB[v][0], vertBlockLightRGB[v][1], vertBlockLightRGB[v][2],
                                              vertHorizonWeight[v],
                                              indirect[0], indirect[1], indirect[2]);
                        }
                        if (isTransparent) {
                            transVertexCount += 4;
                        } else {
                            opaqueVertexCount += 4;
                        }

                        if (aoValues[0] + aoValues[2] > aoValues[1] + aoValues[3]) {
                            indices.add(baseVertex + 1, baseVertex + 2, baseVertex + 3);
                            indices.add(baseVertex + 1, baseVertex + 3, baseVertex);
                        } else {
                            indices.add(baseVertex, baseVertex + 1, baseVertex + 2);
                            indices.add(baseVertex, baseVertex + 2, baseVertex + 3);
                        }
                    }
                }
            }
        }

        MeshData opaqueData = opaqueVerts.size() > 0 ? new MeshData(opaqueVerts.toArray(), opaqueIndices.toArray()) : null;
        MeshData transparentData = transVerts.size() > 0 ? new MeshData(transVerts.toArray(), transIndices.toArray()) : null;
        return new MeshData[] { opaqueData, transparentData };
    }

    /**
     * MINIMAL 6-float vertex: x, y, z, u, v, light
     */
    private void addVertexMinimal(FloatArrayBuilder verts, float x, float y, float z,
                                   float u, float v, float light) {
        verts.add(x);
        verts.add(y);
        verts.add(z);
        verts.add(u);
        verts.add(v);
        verts.add(light);
    }

    // Legacy 13-float vertex (kept for compatibility but not used in minimal mode)
    private void addVertexPrimitive(FloatArrayBuilder verts, float x, float y, float z,
                                    float u, float v, float skyVisibility,
                                    float blockLightR, float blockLightG, float blockLightB,
                                    float horizonWeight,
                                    float indirectR, float indirectG, float indirectB) {
        verts.add(x);
        verts.add(y);
        verts.add(z);
        verts.add(u);
        verts.add(v);
        verts.add(skyVisibility);
        verts.add(blockLightR);
        verts.add(blockLightG);
        verts.add(blockLightB);
        verts.add(horizonWeight);
        verts.add(indirectR);
        verts.add(indirectG);
        verts.add(indirectB);
    }
    
    private void addQuadIndicesPrimitive(IntArrayBuilder indices, int base) {
        indices.add(base, base + 1, base + 2, base, base + 2, base + 3);
    }
    
    private int meshTorchPrimitive(FloatArrayBuilder verts, IntArrayBuilder indices, int vertexCount,
                                    float wx, float wy, float wz, Block block, TextureAtlas atlas) {
        float[] uv = atlas.getUV(block.getTextureIndex(0));
        float light = 1.0f;  // Torches are self-lit

        float pad = 6.0f / 16.0f;
        float x0 = wx + pad, x1 = wx + 1.0f - pad;
        float z0 = wz + pad, z1 = wz + 1.0f - pad;
        float y0 = wy, y1 = wy + 10.0f / 16.0f;
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        int base = vertexCount;
        // Top (+Y)
        addVertexMinimal(verts, x0, y1, z0, u0, v0, light);
        addVertexMinimal(verts, x0, y1, z1, u0, v1, light);
        addVertexMinimal(verts, x1, y1, z1, u1, v1, light);
        addVertexMinimal(verts, x1, y1, z0, u1, v0, light);
        addQuadIndicesPrimitive(indices, base); base += 4;
        // Bottom (-Y)
        addVertexMinimal(verts, x0, y0, z1, u0, v0, light * 0.45f);
        addVertexMinimal(verts, x0, y0, z0, u0, v1, light * 0.45f);
        addVertexMinimal(verts, x1, y0, z0, u1, v1, light * 0.45f);
        addVertexMinimal(verts, x1, y0, z1, u1, v0, light * 0.45f);
        addQuadIndicesPrimitive(indices, base); base += 4;
        // North (-Z)
        addVertexMinimal(verts, x1, y1, z0, u0, v0, light * 0.7f);
        addVertexMinimal(verts, x1, y0, z0, u0, v1, light * 0.7f);
        addVertexMinimal(verts, x0, y0, z0, u1, v1, light * 0.7f);
        addVertexMinimal(verts, x0, y1, z0, u1, v0, light * 0.7f);
        addQuadIndicesPrimitive(indices, base); base += 4;
        // South (+Z)
        addVertexMinimal(verts, x0, y1, z1, u0, v0, light * 0.7f);
        addVertexMinimal(verts, x0, y0, z1, u0, v1, light * 0.7f);
        addVertexMinimal(verts, x1, y0, z1, u1, v1, light * 0.7f);
        addVertexMinimal(verts, x1, y1, z1, u1, v0, light * 0.7f);
        addQuadIndicesPrimitive(indices, base); base += 4;
        // East (+X)
        addVertexMinimal(verts, x1, y1, z1, u0, v0, light * 0.8f);
        addVertexMinimal(verts, x1, y0, z1, u0, v1, light * 0.8f);
        addVertexMinimal(verts, x1, y0, z0, u1, v1, light * 0.8f);
        addVertexMinimal(verts, x1, y1, z0, u1, v0, light * 0.8f);
        addQuadIndicesPrimitive(indices, base); base += 4;
        // West (-X)
        addVertexMinimal(verts, x0, y1, z0, u0, v0, light * 0.6f);
        addVertexMinimal(verts, x0, y0, z0, u0, v1, light * 0.6f);
        addVertexMinimal(verts, x0, y0, z1, u1, v1, light * 0.6f);
        addVertexMinimal(verts, x0, y1, z1, u1, v0, light * 0.6f);
        addQuadIndicesPrimitive(indices, base);

        return vertexCount + 24;
    }
    
    private int meshCrossPrimitive(FloatArrayBuilder verts, IntArrayBuilder indices, int vertexCount,
                                    float wx, float wy, float wz, Block block, TextureAtlas atlas) {
        float[] uv = atlas.getUV(block.getTextureIndex(0));
        float light = 0.85f;  // Slightly dimmer for foliage

        float[][] crossVerts = {
            {0, 0, 0}, {1, 0, 1}, {1, 1, 1}, {0, 1, 0},
            {1, 0, 0}, {0, 0, 1}, {0, 1, 1}, {1, 1, 0}
        };
        int[][] crossUV = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};

        for (int q = 0; q < 2; q++) {
            for (int v = 0; v < 4; v++) {
                int idx = q * 4 + v;
                float vx = wx + crossVerts[idx][0];
                float vy = wy + crossVerts[idx][1];
                float vz = wz + crossVerts[idx][2];
                float u = (crossUV[v][0] == 0) ? uv[0] : uv[2];
                float vCoord = (crossUV[v][1] == 0) ? uv[1] : uv[3];
                addVertexMinimal(verts, vx, vy, vz, u, vCoord, light);
            }
            addQuadIndicesPrimitive(indices, vertexCount);
            vertexCount += 4;
        }
        return vertexCount;
    }
    
    private int meshFlatQuadPrimitive(FloatArrayBuilder verts, IntArrayBuilder indices, int vertexCount,
                                       float wx, float wy, float wz, Block block, TextureAtlas atlas) {
        float[] uv = atlas.getUV(block.getTextureIndex(0));
        float light = 0.85f;
        float yOffset = 0.01f;

        float[][] quadVerts = {{0, yOffset, 0}, {1, yOffset, 0}, {1, yOffset, 1}, {0, yOffset, 1}};
        int[][] quadUV = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

        for (int v = 0; v < 4; v++) {
            float vx = wx + quadVerts[v][0];
            float vy = wy + quadVerts[v][1];
            float vz = wz + quadVerts[v][2];
            float u = (quadUV[v][0] == 0) ? uv[0] : uv[2];
            float vCoord = (quadUV[v][1] == 0) ? uv[1] : uv[3];
            addVertexMinimal(verts, vx, vy, vz, u, vCoord, light);
        }
        addQuadIndicesPrimitive(indices, vertexCount);
        return vertexCount + 4;
    }
    
    private int meshFlatSlabPrimitive(FloatArrayBuilder verts, IntArrayBuilder indices, int vertexCount,
                                       float wx, float wy, float wz, Block block, TextureAtlas atlas) {
        float[] uv = atlas.getUV(block.getTextureIndex(0));
        float light = 0.8f;
        float slabHeight = 2f / 16f;

        float[][] topVerts = {{0, slabHeight, 0}, {1, slabHeight, 0}, {1, slabHeight, 1}, {0, slabHeight, 1}};
        int[][] quadUV = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

        for (int v = 0; v < 4; v++) {
            float vx = wx + topVerts[v][0];
            float vy = wy + topVerts[v][1];
            float vz = wz + topVerts[v][2];
            float u = (quadUV[v][0] == 0) ? uv[0] : uv[2];
            float vCoord = (quadUV[v][1] == 0) ? uv[1] : uv[3];
            addVertexMinimal(verts, vx, vy, vz, u, vCoord, light);
        }
        addQuadIndicesPrimitive(indices, vertexCount);
        return vertexCount + 4;
    }

    /**
     * Check if a block at (bx + offset) is an occluder for AO purposes.
     * An occluder is a solid, opaque block.
     */
    private boolean isOccluder(WorldAccess world, int bx, int by, int bz, int[] offset) {
        int wx = bx + offset[0];
        int wy = by + offset[1];
        int wz = bz + offset[2];
        if (wy < 0 || wy >= WorldConstants.WORLD_HEIGHT) return false;
        int blockId = world.getBlock(wx, wy, wz);
        Block block = Blocks.get(blockId);
        return block.solid() && !block.transparent();
    }

    /**
     * Sample block light for a vertex, similar to sky light sampling.
     * @deprecated Use sampleVertexBlockLightRGB for Phase 4 colored lighting.
     */
    @Deprecated
    private float sampleVertexBlockLight(WorldAccess world, int bx, int by, int bz,
                                          int face, int[][] aoNeighbors) {
        int[] normal = FACE_NORMALS[face];
        int fnx = bx + normal[0];
        int fny = by + normal[1];
        int fnz = bz + normal[2];

        float totalLight = getBlockLightSafe(world, fnx, fny, fnz);
        int count = 1;

        for (int[] ao : aoNeighbors) {
            int sx = bx + ao[0];
            int sy = by + ao[1];
            int sz = bz + ao[2];
            int blockId = world.getBlock(sx, sy, sz);
            Block block = Blocks.get(blockId);
            if (!block.solid() || block.transparent()) {
                totalLight += getBlockLightSafe(world, sx, sy, sz);
                count++;
            }
        }

        return totalLight / count;
    }

    /**
     * Phase 4: Sample RGB block light for a vertex.
     * Averages samples from face-normal position and AO neighbor positions.
     * @return RGB light values [R, G, B] each 0-1
     */
    private float[] sampleVertexBlockLightRGB(WorldAccess world, int bx, int by, int bz,
                                               int face, int[][] aoNeighbors) {
        int[] normal = FACE_NORMALS[face];
        int fnx = bx + normal[0];
        int fny = by + normal[1];
        int fnz = bz + normal[2];

        float[] totalLight = getBlockLightRGBSafe(world, fnx, fny, fnz);
        int count = 1;

        for (int[] ao : aoNeighbors) {
            int sx = bx + ao[0];
            int sy = by + ao[1];
            int sz = bz + ao[2];
            int blockId = world.getBlock(sx, sy, sz);
            Block block = Blocks.get(blockId);
            if (!block.solid() || block.transparent()) {
                float[] sample = getBlockLightRGBSafe(world, sx, sy, sz);
                totalLight[0] += sample[0];
                totalLight[1] += sample[1];
                totalLight[2] += sample[2];
                count++;
            }
        }

        return new float[] {
            totalLight[0] / count,
            totalLight[1] / count,
            totalLight[2] / count
        };
    }

    private float getBlockLightSafe(WorldAccess world, int x, int y, int z) {
        if (y >= WorldConstants.WORLD_HEIGHT) return 0.0f;
        if (y < 0) return 0.0f;
        return world.getBlockLight(x, y, z);
    }

    private float[] getBlockLightRGBSafe(WorldAccess world, int x, int y, int z) {
        if (y >= WorldConstants.WORLD_HEIGHT || y < 0) {
            return new float[] {0, 0, 0};
        }
        return world.getBlockLightRGB(x, y, z);
    }

    private float getSkyVisibilitySafe(WorldAccess world, int x, int y, int z) {
        if (y >= WorldConstants.WORLD_HEIGHT) return 1.0f;
        if (y < 0) return 0.0f;
        return world.getSkyVisibility(x, y, z);
    }

    /**
     * Phase 4: Add vertex with RGB block light (13 floats per vertex).
     * Format: [x, y, z, u, v, skyVis, blockR, blockG, blockB, horizonWeight, indirectR, indirectG, indirectB]
     */
    private void addVertex(List<Float> verts, float x, float y, float z,
                          float u, float v, float skyVisibility, 
                          float blockLightR, float blockLightG, float blockLightB,
                          float horizonWeight,
                          float indirectR, float indirectG, float indirectB) {
        verts.add(x);
        verts.add(y);
        verts.add(z);
        verts.add(u);
        verts.add(v);
        verts.add(skyVisibility);
        verts.add(blockLightR);
        verts.add(blockLightG);
        verts.add(blockLightB);
        verts.add(horizonWeight);
        verts.add(indirectR);
        verts.add(indirectG);
        verts.add(indirectB);
    }
    
    /**
     * Sample indirect lighting from probe manager at world position.
     * Returns [0,0,0] if probe manager is not available.
     */
    private float[] sampleIndirect(float worldX, float worldY, float worldZ) {
        if (probeManager != null) {
            return probeManager.sampleIndirect(worldX, worldY, worldZ);
        }
        return new float[] {0, 0, 0};
    }

    /**
     * Mesh a torch as a small centered cube (4/16 wide, 10/16 tall)
     * with bright light value for the glow effect.
     * Phase 4: Use RGB block light color for torch self-illumination.
     */
    private int meshTorch(List<Float> verts, List<Integer> indices, int vertexCount,
                           float wx, float wy, float wz, Block block, TextureAtlas atlas) {
        float[] uv = atlas.getUV(block.getTextureIndex(0));
        float skyL = 1.0f;  // fully bright sky component
        float hrzW = 0.3f;  // balanced horizon weight for torches
        
        // Phase 4: Get torch color from LightEmitters (self-illuminated at full intensity)
        float[] torchColor = LightEmitters.getLightColorRGB(block.id());
        float blkR = torchColor != null ? torchColor[0] : 1.0f;
        float blkG = torchColor != null ? torchColor[1] : 0.8f;
        float blkB = torchColor != null ? torchColor[2] : 0.5f;
        
        // Sample indirect lighting at torch position
        float[] ind = sampleIndirect(wx + 0.5f, wy + 0.5f, wz + 0.5f);

        // Inset: centered 4/16 wide, 10/16 tall
        float pad = 6.0f / 16.0f;  // 6/16 padding on each side
        float x0 = wx + pad, x1 = wx + 1.0f - pad;
        float z0 = wz + pad, z1 = wz + 1.0f - pad;
        float y0 = wy, y1 = wy + 10.0f / 16.0f;

        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        // 6 faces of the small cube
        int base = vertexCount;

        // Top (+Y)
        addVertex(verts, x0, y1, z0, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y1, z1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y1, z1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y1, z0, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // Bottom (-Y)
        addVertex(verts, x0, y0, z1, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y0, z0, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y0, z0, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y0, z1, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // North (-Z)
        addVertex(verts, x1, y1, z0, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y0, z0, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y0, z0, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y1, z0, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // South (+Z)
        addVertex(verts, x0, y1, z1, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y0, z1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y0, z1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y1, z1, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // East (+X)
        addVertex(verts, x1, y1, z1, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y0, z1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y0, z0, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x1, y1, z0, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // West (-X)
        addVertex(verts, x0, y1, z0, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y0, z0, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y0, z1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, x0, y1, z1, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        return base;
    }

    /**
     * Mesh a flower/cross-shaped block as two intersecting quads (X-pattern).
     * Classic Minecraft billboard rendering for plants.
     * Phase 4: No block light emission for flowers (blkR/G/B = 0).
     */
    private int meshCross(List<Float> verts, List<Integer> indices, int vertexCount,
                           float wx, float wy, float wz, Block block, TextureAtlas atlas) {
        float[] uv = atlas.getUV(block.getTextureIndex(0));
        float skyL = 0.85f;  // slightly dimmer sky light
        float blkR = 0.0f, blkG = 0.0f, blkB = 0.0f;  // no block light emission
        float hrzW = 0.3f;   // balanced horizon weight
        
        // Sample indirect lighting at flower center
        float[] ind = sampleIndirect(wx + 0.5f, wy + 0.5f, wz + 0.5f);

        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        int base = vertexCount;

        // Diagonal 1: corner (0,0)→(1,1) — front and back
        addVertex(verts, wx, wy + 1, wz, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, wy, wz, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, wy, wz + 1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, wy + 1, wz + 1, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // Back face of diagonal 1
        addVertex(verts, wx + 1, wy + 1, wz + 1, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, wy, wz + 1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, wy, wz, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, wy + 1, wz, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // Diagonal 2: corner (1,0)→(0,1) — front and back
        addVertex(verts, wx + 1, wy + 1, wz, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, wy, wz, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, wy, wz + 1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, wy + 1, wz + 1, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // Back face of diagonal 2
        addVertex(verts, wx, wy + 1, wz + 1, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, wy, wz + 1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, wy, wz, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, wy + 1, wz, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        return base;
    }

    /**
     * Mesh a flat quad on the ground surface (for rails, redstone wire).
     * Renders as a single horizontal quad slightly above the block floor.
     * Phase 4: RGB block light = 0 for non-emissive blocks.
     */
    private int meshFlatQuad(List<Float> verts, List<Integer> indices, int vertexCount,
                              float wx, float wy, float wz, Block block, TextureAtlas atlas) {
        float[] uv = atlas.getUV(block.getTextureIndex(0));
        float skyL = 0.85f;
        float blkR = 0.0f, blkG = 0.0f, blkB = 0.0f;
        float hrzW = 0.3f;
        
        // Sample indirect lighting
        float[] ind = sampleIndirect(wx + 0.5f, wy + 0.1f, wz + 0.5f);

        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float yOff = wy + 0.02f;  // slightly above ground to avoid z-fighting
        int base = vertexCount;

        // Top face
        addVertex(verts, wx, yOff, wz, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, yOff, wz + 1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, yOff, wz + 1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, yOff, wz, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // Bottom face (visible from below)
        addVertex(verts, wx + 1, yOff, wz, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, yOff, wz + 1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, yOff, wz + 1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, yOff, wz, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        return base;
    }

    /**
     * Mesh a flat slab on the ground surface (for redstone repeater).
     * Renders as a thin box (2/16 tall) on the ground.
     * Phase 4: RGB block light = 0 for non-emissive blocks.
     */
    private int meshFlatSlab(List<Float> verts, List<Integer> indices, int vertexCount,
                              float wx, float wy, float wz, Block block, TextureAtlas atlas) {
        float[] uv = atlas.getUV(block.getTextureIndex(0));
        float skyL = 0.8f;
        float blkR = 0.0f, blkG = 0.0f, blkB = 0.0f;
        float hrzW = 0.3f;
        
        // Sample indirect lighting
        float[] ind = sampleIndirect(wx + 0.5f, wy + 0.1f, wz + 0.5f);

        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        float y0 = wy;
        float y1 = wy + 2.0f / 16.0f;
        int base = vertexCount;

        // Top (+Y)
        addVertex(verts, wx, y1, wz, u0, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, y1, wz + 1, u0, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y1, wz + 1, u1, v1, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y1, wz, u1, v0, skyL, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // North (-Z)
        addVertex(verts, wx + 1, y1, wz, u0, v0, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y0, wz, u0, v1, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, y0, wz, u1, v1, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, y1, wz, u1, v0, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // South (+Z)
        addVertex(verts, wx, y1, wz + 1, u0, v0, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, y0, wz + 1, u0, v1, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y0, wz + 1, u1, v1, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y1, wz + 1, u1, v0, skyL * 0.7f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // East (+X)
        addVertex(verts, wx + 1, y1, wz + 1, u0, v0, skyL * 0.8f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y0, wz + 1, u0, v1, skyL * 0.8f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y0, wz, u1, v1, skyL * 0.8f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx + 1, y1, wz, u1, v0, skyL * 0.8f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        // West (-X)
        addVertex(verts, wx, y1, wz, u0, v0, skyL * 0.6f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, y0, wz, u0, v1, skyL * 0.6f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, y0, wz + 1, u1, v1, skyL * 0.6f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addVertex(verts, wx, y1, wz + 1, u1, v0, skyL * 0.6f, blkR, blkG, blkB, hrzW, ind[0], ind[1], ind[2]);
        addQuadIndices(indices, base); base += 4;

        return base;
    }

    /** Add standard quad indices (two triangles) for 4 vertices starting at base. */
    private void addQuadIndices(List<Integer> indices, int base) {
        indices.add(base);
        indices.add(base + 1);
        indices.add(base + 2);
        indices.add(base);
        indices.add(base + 2);
        indices.add(base + 3);
    }
}
