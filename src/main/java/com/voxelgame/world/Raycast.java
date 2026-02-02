package com.voxelgame.world;

import org.joml.Vector3f;

/**
 * Voxel ray traversal (DDA algorithm). Casts a ray through the block grid
 * and returns the first solid block hit plus the face normal.
 */
public class Raycast {

    /** Result of a raycast hit. */
    public record HitResult(int x, int y, int z, int nx, int ny, int nz) {}

    /**
     * Cast a ray from origin along direction, up to maxDist blocks.
     * Returns HitResult if a solid block is hit, null otherwise.
     */
    public static HitResult cast(WorldAccess world, Vector3f origin, Vector3f direction, float maxDist) {
        float dirX = direction.x, dirY = direction.y, dirZ = direction.z;

        // Normalize direction
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (len < 1e-8f) return null;
        dirX /= len;
        dirY /= len;
        dirZ /= len;

        // Current voxel coordinates
        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        // Step direction
        int stepX = dirX > 0 ? 1 : (dirX < 0 ? -1 : 0);
        int stepY = dirY > 0 ? 1 : (dirY < 0 ? -1 : 0);
        int stepZ = dirZ > 0 ? 1 : (dirZ < 0 ? -1 : 0);

        // tMax: distance along ray to next voxel boundary
        float tMaxX = intBound(origin.x, dirX);
        float tMaxY = intBound(origin.y, dirY);
        float tMaxZ = intBound(origin.z, dirZ);

        // tDelta: distance along ray to cross one voxel
        float tDeltaX = (stepX != 0) ? Math.abs(1.0f / dirX) : Float.MAX_VALUE;
        float tDeltaY = (stepY != 0) ? Math.abs(1.0f / dirY) : Float.MAX_VALUE;
        float tDeltaZ = (stepZ != 0) ? Math.abs(1.0f / dirZ) : Float.MAX_VALUE;

        // Track which face we entered from
        int faceNx = 0, faceNy = 0, faceNz = 0;

        float dist = 0;
        while (dist < maxDist) {
            // Check current block (solid blocks + non-solid placeables like torches, flowers, rails)
            if (y >= 0 && y < WorldConstants.WORLD_HEIGHT) {
                int blockId = world.getBlock(x, y, z);
                if (blockId != 0) {
                    Block block = Blocks.get(blockId);
                    if (block.solid() || Blocks.isNonSolidPlaceable(blockId)) {
                        return new HitResult(x, y, z, faceNx, faceNy, faceNz);
                    }
                }
            }

            // Step to next voxel
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                dist = tMaxX;
                x += stepX;
                tMaxX += tDeltaX;
                faceNx = -stepX;
                faceNy = 0;
                faceNz = 0;
            } else if (tMaxY < tMaxZ) {
                dist = tMaxY;
                y += stepY;
                tMaxY += tDeltaY;
                faceNx = 0;
                faceNy = -stepY;
                faceNz = 0;
            } else {
                dist = tMaxZ;
                z += stepZ;
                tMaxZ += tDeltaZ;
                faceNx = 0;
                faceNy = 0;
                faceNz = -stepZ;
            }
        }

        return null;
    }

    private static float intBound(float s, float ds) {
        if (ds > 0) {
            return ((float) Math.ceil(s) - s) / ds;
        } else if (ds < 0) {
            return (s - (float) Math.floor(s)) / (-ds);
        } else {
            return Float.MAX_VALUE;
        }
    }
}
