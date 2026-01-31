package com.voxelgame.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.voxelgame.world.WorldConstants;

/**
 * View frustum culling. Extracts frustum planes from the VP matrix
 * and tests chunk AABBs for visibility.
 */
public class Frustum {

    private final float[][] planes = new float[6][4]; // 6 planes, each [a, b, c, d]

    /**
     * Extract frustum planes from combined projection*view matrix.
     */
    public void update(Matrix4f projView) {
        // Use a temporary float array to extract matrix values
        float[] m = new float[16];
        projView.get(m);

        // Left plane
        planes[0][0] = m[3] + m[0];
        planes[0][1] = m[7] + m[4];
        planes[0][2] = m[11] + m[8];
        planes[0][3] = m[15] + m[12];

        // Right plane
        planes[1][0] = m[3] - m[0];
        planes[1][1] = m[7] - m[4];
        planes[1][2] = m[11] - m[8];
        planes[1][3] = m[15] - m[12];

        // Bottom plane
        planes[2][0] = m[3] + m[1];
        planes[2][1] = m[7] + m[5];
        planes[2][2] = m[11] + m[9];
        planes[2][3] = m[15] + m[13];

        // Top plane
        planes[3][0] = m[3] - m[1];
        planes[3][1] = m[7] - m[5];
        planes[3][2] = m[11] - m[9];
        planes[3][3] = m[15] - m[13];

        // Near plane
        planes[4][0] = m[3] + m[2];
        planes[4][1] = m[7] + m[6];
        planes[4][2] = m[11] + m[10];
        planes[4][3] = m[15] + m[14];

        // Far plane
        planes[5][0] = m[3] - m[2];
        planes[5][1] = m[7] - m[6];
        planes[5][2] = m[11] - m[10];
        planes[5][3] = m[15] - m[14];

        // Normalize planes
        for (int i = 0; i < 6; i++) {
            float len = (float) Math.sqrt(
                planes[i][0] * planes[i][0] +
                planes[i][1] * planes[i][1] +
                planes[i][2] * planes[i][2]);
            if (len > 0) {
                planes[i][0] /= len;
                planes[i][1] /= len;
                planes[i][2] /= len;
                planes[i][3] /= len;
            }
        }
    }

    /**
     * Test if an AABB (defined by chunk position) is inside or intersects the frustum.
     */
    public boolean isChunkVisible(int chunkX, int chunkZ) {
        float minX = chunkX * WorldConstants.CHUNK_SIZE;
        float minY = 0;
        float minZ = chunkZ * WorldConstants.CHUNK_SIZE;
        float maxX = minX + WorldConstants.CHUNK_SIZE;
        float maxY = WorldConstants.WORLD_HEIGHT;
        float maxZ = minZ + WorldConstants.CHUNK_SIZE;

        for (int i = 0; i < 6; i++) {
            float a = planes[i][0], b = planes[i][1], c = planes[i][2], d = planes[i][3];

            // Find the positive vertex (the one farthest in the direction of the plane normal)
            float px = (a >= 0) ? maxX : minX;
            float py = (b >= 0) ? maxY : minY;
            float pz = (c >= 0) ? maxZ : minZ;

            if (a * px + b * py + c * pz + d < 0) {
                return false; // Entirely outside this plane
            }
        }
        return true;
    }
}
