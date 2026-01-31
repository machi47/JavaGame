package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * AABB collision detection and resolution against the voxel world.
 *
 * Player hitbox: 0.6 wide × 1.8 tall × 0.6 deep.
 * Position convention: getPosition() = eye level.
 *   feet  = pos.y - EYE_HEIGHT
 *   head  = pos.y - EYE_HEIGHT + HEIGHT  (= pos.y + 0.18)
 *
 * Resolution: sweep axis by axis (Y first for ground detection, then X, then Z)
 * to prevent tunneling. On each axis, clip velocity to the first collision.
 *
 * Step-up: when horizontal collision detected and player is on ground,
 * attempt to step up ledges ≤ STEP_HEIGHT (0.5 blocks) automatically.
 */
public class Collision {

    private static final float EPSILON     = 0.001f;
    private static final float STEP_HEIGHT = 0.5f;  // max auto step-up height

    /**
     * Resolve movement with collision against the world.
     * Modifies pos and vel in-place.
     *
     * @param pos    eye-level position (modified)
     * @param vel    velocity (modified — zeroed on collision axes)
     * @param dt     delta time
     * @param world  the block world
     * @param player the player (for setting onGround)
     */
    public static void resolveMovement(Vector3f pos, Vector3f vel, float dt, World world, Player player) {
        float dx = vel.x * dt;
        float dy = vel.y * dt;
        float dz = vel.z * dt;

        float halfW = Player.HALF_WIDTH;
        float eyeH  = Player.EYE_HEIGHT;
        float headH = Player.HEIGHT - eyeH;

        // --- Resolve Y axis first (most important for ground detection) ---
        if (dy != 0) {
            float clipped = sweepAxisY(pos, halfW, eyeH, headH, dy, world);
            if (Math.abs(clipped - dy) > EPSILON) {
                if (vel.y < 0) {
                    player.setOnGround(true);
                }
                vel.y = 0;
                dy = clipped;
            } else {
                if (dy < -EPSILON) {
                    player.setOnGround(false);
                }
            }
            pos.y += clipped;
        } else {
            // Probe below to check ground when stationary
            float probe = sweepAxisY(pos, halfW, eyeH, headH, -0.05f, world);
            player.setOnGround(Math.abs(probe - (-0.05f)) > EPSILON);
        }

        // --- Resolve X axis (with step-up attempt) ---
        if (dx != 0) {
            float clipped = sweepAxisX(pos, halfW, eyeH, headH, dx, world);
            if (Math.abs(clipped - dx) > EPSILON) {
                // Horizontal collision — try step-up if on ground
                if (player.isOnGround() && tryStepUp(pos, vel, halfW, eyeH, headH, dx, 0, world)) {
                    // Step-up succeeded — dx was applied via position adjustment
                } else {
                    vel.x = 0;
                    pos.x += clipped;
                }
            } else {
                pos.x += clipped;
            }
        }

        // --- Resolve Z axis (with step-up attempt) ---
        if (dz != 0) {
            float clipped = sweepAxisZ(pos, halfW, eyeH, headH, dz, world);
            if (Math.abs(clipped - dz) > EPSILON) {
                // Horizontal collision — try step-up if on ground
                if (player.isOnGround() && tryStepUp(pos, vel, halfW, eyeH, headH, 0, dz, world)) {
                    // Step-up succeeded
                } else {
                    vel.z = 0;
                    pos.z += clipped;
                }
            } else {
                pos.z += clipped;
            }
        }
    }

    // ============================================================
    // Step-up logic
    // ============================================================

    /**
     * Attempt to step up a ledge. Temporarily raises position by up to STEP_HEIGHT,
     * tries to move horizontally, then settles back down.
     *
     * @return true if step-up succeeded and pos was modified
     */
    private static boolean tryStepUp(Vector3f pos, Vector3f vel,
                                      float halfW, float eyeH, float headH,
                                      float dx, float dz, World world) {
        // 1. Check if we can move upward by STEP_HEIGHT
        float upClipped = sweepAxisY(pos, halfW, eyeH, headH, STEP_HEIGHT, world);
        if (upClipped < EPSILON) return false; // can't step up at all (ceiling)

        // 2. Temporarily raise position
        float savedY = pos.y;
        pos.y += upClipped;

        // 3. Try horizontal movement at raised height
        boolean moved = false;
        if (dx != 0) {
            float hClipped = sweepAxisX(pos, halfW, eyeH, headH, dx, world);
            if (Math.abs(hClipped) > EPSILON) {
                pos.x += hClipped;
                moved = true;
            }
        }
        if (dz != 0) {
            float hClipped = sweepAxisZ(pos, halfW, eyeH, headH, dz, world);
            if (Math.abs(hClipped) > EPSILON) {
                pos.z += hClipped;
                moved = true;
            }
        }

        if (!moved) {
            // Couldn't move horizontally even after stepping up — revert
            pos.y = savedY;
            return false;
        }

        // 4. Settle back down (find the ground at the new XZ position)
        float downClipped = sweepAxisY(pos, halfW, eyeH, headH, -upClipped, world);
        pos.y += downClipped;

        return true;
    }

    // ============================================================
    // Axis sweep functions
    // ============================================================

    private static float sweepAxisY(Vector3f pos, float halfW, float eyeH, float headH,
                                     float dy, World world) {
        if (dy == 0) return 0;

        float minX = pos.x - halfW;
        float maxX = pos.x + halfW;
        float minZ = pos.z - halfW;
        float maxZ = pos.z + halfW;
        float feetY = pos.y - eyeH;
        float headY = pos.y + headH;

        int bx0 = floor(minX + EPSILON);
        int bx1 = floor(maxX - EPSILON);
        int bz0 = floor(minZ + EPSILON);
        int bz1 = floor(maxZ - EPSILON);

        float result = dy;

        if (dy < 0) {
            float targetFeetY = feetY + dy;
            int by0 = floor(targetFeetY + EPSILON);
            int by1 = floor(feetY - EPSILON);

            for (int bx = bx0; bx <= bx1; bx++) {
                for (int bz = bz0; bz <= bz1; bz++) {
                    for (int by = by1; by >= by0; by--) {
                        if (isSolid(world, bx, by, bz)) {
                            float blockTop = by + 1.0f;
                            float maxDy = blockTop - feetY;
                            if (maxDy > result) {
                                result = maxDy;
                            }
                        }
                    }
                }
            }
        } else {
            float targetHeadY = headY + dy;
            int by0 = floor(headY + EPSILON);
            int by1 = floor(targetHeadY - EPSILON);

            for (int bx = bx0; bx <= bx1; bx++) {
                for (int bz = bz0; bz <= bz1; bz++) {
                    for (int by = by0; by <= by1; by++) {
                        if (isSolid(world, bx, by, bz)) {
                            float blockBottom = (float) by;
                            float maxDy = blockBottom - headY;
                            if (maxDy < result) {
                                result = maxDy;
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    private static float sweepAxisX(Vector3f pos, float halfW, float eyeH, float headH,
                                     float dx, World world) {
        if (dx == 0) return 0;

        float minZ = pos.z - halfW;
        float maxZ = pos.z + halfW;
        float feetY = pos.y - eyeH;
        float headY = pos.y + headH;

        int bz0 = floor(minZ + EPSILON);
        int bz1 = floor(maxZ - EPSILON);
        int by0 = floor(feetY + EPSILON);
        int by1 = floor(headY - EPSILON);

        float result = dx;

        if (dx < 0) {
            float edgeX = pos.x - halfW;
            float targetX = edgeX + dx;
            int bxFrom = floor(targetX + EPSILON);
            int bxTo   = floor(edgeX - EPSILON);

            for (int bx = bxTo; bx >= bxFrom; bx--) {
                for (int bz = bz0; bz <= bz1; bz++) {
                    for (int by = by0; by <= by1; by++) {
                        if (isSolid(world, bx, by, bz)) {
                            float blockRight = bx + 1.0f;
                            float maxDx = blockRight - edgeX + EPSILON;
                            if (maxDx > result) result = maxDx;
                        }
                    }
                }
            }
        } else {
            float edgeX = pos.x + halfW;
            float targetX = edgeX + dx;
            int bxFrom = floor(edgeX + EPSILON);
            int bxTo   = floor(targetX - EPSILON);

            for (int bx = bxFrom; bx <= bxTo; bx++) {
                for (int bz = bz0; bz <= bz1; bz++) {
                    for (int by = by0; by <= by1; by++) {
                        if (isSolid(world, bx, by, bz)) {
                            float blockLeft = (float) bx;
                            float maxDx = blockLeft - edgeX - EPSILON;
                            if (maxDx < result) result = maxDx;
                        }
                    }
                }
            }
        }

        return result;
    }

    private static float sweepAxisZ(Vector3f pos, float halfW, float eyeH, float headH,
                                     float dz, World world) {
        if (dz == 0) return 0;

        float minX = pos.x - halfW;
        float maxX = pos.x + halfW;
        float feetY = pos.y - eyeH;
        float headY = pos.y + headH;

        int bx0 = floor(minX + EPSILON);
        int bx1 = floor(maxX - EPSILON);
        int by0 = floor(feetY + EPSILON);
        int by1 = floor(headY - EPSILON);

        float result = dz;

        if (dz < 0) {
            float edgeZ = pos.z - halfW;
            float targetZ = edgeZ + dz;
            int bzFrom = floor(targetZ + EPSILON);
            int bzTo   = floor(edgeZ - EPSILON);

            for (int bz = bzTo; bz >= bzFrom; bz--) {
                for (int bx = bx0; bx <= bx1; bx++) {
                    for (int by = by0; by <= by1; by++) {
                        if (isSolid(world, bx, by, bz)) {
                            float blockFront = bz + 1.0f;
                            float maxDz = blockFront - edgeZ + EPSILON;
                            if (maxDz > result) result = maxDz;
                        }
                    }
                }
            }
        } else {
            float edgeZ = pos.z + halfW;
            float targetZ = edgeZ + dz;
            int bzFrom = floor(edgeZ + EPSILON);
            int bzTo   = floor(targetZ - EPSILON);

            for (int bz = bzFrom; bz <= bzTo; bz++) {
                for (int bx = bx0; bx <= bx1; bx++) {
                    for (int by = by0; by <= by1; by++) {
                        if (isSolid(world, bx, by, bz)) {
                            float blockBack = (float) bz;
                            float maxDz = blockBack - edgeZ - EPSILON;
                            if (maxDz < result) result = maxDz;
                        }
                    }
                }
            }
        }

        return result;
    }

    // ---- Helpers ----

    private static boolean isSolid(World world, int x, int y, int z) {
        int blockId = world.getBlock(x, y, z);
        return Blocks.get(blockId).solid();
    }

    private static int floor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
