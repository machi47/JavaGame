package com.voxelgame.world.mesh;

import com.voxelgame.render.TextureAtlas;
import com.voxelgame.world.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple per-face mesher. Emits two triangles for every visible block face.
 * Vertex format: [x, y, z, u, v, light] per vertex, 4 vertices per face.
 */
public class NaiveMesher implements Mesher {

    // Face indices: 0=top(+Y), 1=bottom(-Y), 2=north(-Z), 3=south(+Z), 4=east(+X), 5=west(-X)
    private static final float[] FACE_LIGHT = {1.0f, 0.6f, 0.8f, 0.8f, 0.85f, 0.75f};

    // Direction offsets for neighbor checking [dx, dy, dz]
    private static final int[][] FACE_NORMALS = {
        { 0,  1,  0}, // top
        { 0, -1,  0}, // bottom
        { 0,  0, -1}, // north
        { 0,  0,  1}, // south
        { 1,  0,  0}, // east
        {-1,  0,  0}, // west
    };

    private TextureAtlas atlas;

    public NaiveMesher(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    @Override
    public ChunkMesh mesh(Chunk chunk, WorldAccess world) {
        List<Float> verts = new ArrayList<>();
        int quadCount = 0;
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

                    float wx = cx + x;
                    float wy = y;
                    float wz = cz + z;

                    for (int face = 0; face < 6; face++) {
                        int nx = x + FACE_NORMALS[face][0];
                        int ny = y + FACE_NORMALS[face][1];
                        int nz = z + FACE_NORMALS[face][2];

                        int neighborId;
                        // Check within chunk or use world access
                        if (nx >= 0 && nx < WorldConstants.CHUNK_SIZE &&
                            ny >= 0 && ny < WorldConstants.WORLD_HEIGHT &&
                            nz >= 0 && nz < WorldConstants.CHUNK_SIZE) {
                            neighborId = chunk.getBlock(nx, ny, nz);
                        } else {
                            neighborId = world.getBlock(cx + nx, ny, cz + nz);
                        }

                        Block neighbor = Blocks.get(neighborId);
                        // Skip face if neighbor is solid and opaque
                        if (neighbor.solid() && !neighbor.transparent()) continue;
                        // Skip face between same transparent blocks (e.g. water-water)
                        if (blockId == neighborId && neighbor.transparent()) continue;

                        int texIdx = block.getTextureIndex(face);
                        float[] uv = atlas.getUV(texIdx);
                        float light = FACE_LIGHT[face];

                        addFace(verts, wx, wy, wz, face, uv[0], uv[1], uv[2], uv[3], light);
                        quadCount++;
                    }
                }
            }
        }

        float[] vertArray = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) {
            vertArray[i] = verts.get(i);
        }

        ChunkMesh mesh = new ChunkMesh();
        mesh.upload(vertArray, quadCount);
        return mesh;
    }

    private void addFace(List<Float> verts, float x, float y, float z,
                         int face, float u0, float v0, float u1, float v1, float light) {
        // Each face is a quad of 4 vertices. Winding order: CCW as seen from outside.
        switch (face) {
            case 0: // Top (+Y) face at y+1
                addVertex(verts, x,     y + 1, z,     u0, v0, light);
                addVertex(verts, x,     y + 1, z + 1, u0, v1, light);
                addVertex(verts, x + 1, y + 1, z + 1, u1, v1, light);
                addVertex(verts, x + 1, y + 1, z,     u1, v0, light);
                break;
            case 1: // Bottom (-Y) face at y
                addVertex(verts, x,     y, z + 1, u0, v0, light);
                addVertex(verts, x,     y, z,     u0, v1, light);
                addVertex(verts, x + 1, y, z,     u1, v1, light);
                addVertex(verts, x + 1, y, z + 1, u1, v0, light);
                break;
            case 2: // North (-Z) face at z
                addVertex(verts, x + 1, y + 1, z, u0, v0, light);
                addVertex(verts, x + 1, y,     z, u0, v1, light);
                addVertex(verts, x,     y,     z, u1, v1, light);
                addVertex(verts, x,     y + 1, z, u1, v0, light);
                break;
            case 3: // South (+Z) face at z+1
                addVertex(verts, x,     y + 1, z + 1, u0, v0, light);
                addVertex(verts, x,     y,     z + 1, u0, v1, light);
                addVertex(verts, x + 1, y,     z + 1, u1, v1, light);
                addVertex(verts, x + 1, y + 1, z + 1, u1, v0, light);
                break;
            case 4: // East (+X) face at x+1
                addVertex(verts, x + 1, y + 1, z + 1, u0, v0, light);
                addVertex(verts, x + 1, y,     z + 1, u0, v1, light);
                addVertex(verts, x + 1, y,     z,     u1, v1, light);
                addVertex(verts, x + 1, y + 1, z,     u1, v0, light);
                break;
            case 5: // West (-X) face at x
                addVertex(verts, x, y + 1, z,     u0, v0, light);
                addVertex(verts, x, y,     z,     u0, v1, light);
                addVertex(verts, x, y,     z + 1, u1, v1, light);
                addVertex(verts, x, y + 1, z + 1, u1, v0, light);
                break;
        }
    }

    private void addVertex(List<Float> verts, float x, float y, float z,
                          float u, float v, float light) {
        verts.add(x);
        verts.add(y);
        verts.add(z);
        verts.add(u);
        verts.add(v);
        verts.add(light);
    }
}
