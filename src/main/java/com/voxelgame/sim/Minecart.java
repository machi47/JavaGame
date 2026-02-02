package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;

/**
 * Minecart entity — rideable, moves on rail blocks.
 * Follows rail paths automatically, powered by slopes, player push, or powered rails.
 * Right-click to mount/dismount.
 *
 * Powered rail (booster track) behavior:
 * - When powered by redstone: accelerates minecart to max speed
 * - When unpowered: acts as a brake, decelerating the minecart
 * - Powered rails give a strong speed boost on contact
 */
public class Minecart extends Entity {

    private static final float CART_HALF_WIDTH = 0.45f;
    private static final float CART_HEIGHT = 0.6f;
    private static final float CART_MAX_HEALTH = 20.0f;

    /** Maximum speed on rails. */
    private static final float RAIL_SPEED = 8.0f;
    /** Maximum speed on powered rails. */
    private static final float POWERED_RAIL_MAX_SPEED = 12.0f;
    /** Slope boost. */
    private static final float SLOPE_BOOST = 4.0f;
    /** Rail friction (slow deceleration). */
    private static final float RAIL_FRICTION = 0.99f;
    /** Push speed when player right-clicks while not mounting. */
    private static final float PUSH_SPEED = 5.0f;
    /** Powered rail acceleration per second. */
    private static final float POWERED_RAIL_ACCEL = 16.0f;
    /** Unpowered powered-rail brake deceleration per second. */
    private static final float BRAKE_DECEL = 12.0f;
    /** Launch speed for powered rail from standstill. */
    private static final float POWERED_RAIL_LAUNCH = 6.0f;

    /** Whether a player is riding. */
    private boolean mounted = false;

    /** Direction of travel: 0=+X, 1=+Z, 2=-X, 3=-Z */
    private int travelDir = 1;

    /** Speed along the rail (positive = forward along travelDir). */
    private float railSpeed = 0;

    /** Whether currently on a rail block. */
    private boolean onRail = false;

    /** Whether currently on a powered rail. */
    private boolean onPoweredRail = false;

    /** Reference to the redstone system for powered rail checks. */
    private RedstoneSystem redstoneSystem;

    public Minecart(float x, float y, float z) {
        super(EntityType.MINECART, x, y, z, CART_HALF_WIDTH, CART_HEIGHT, CART_MAX_HEALTH);
    }

    /** Set the redstone system reference for powered rail queries. */
    public void setRedstoneSystem(RedstoneSystem rs) {
        this.redstoneSystem = rs;
    }

    @Override
    public void update(float dt, World world, Player player) {
        age += dt;
        if (hurtTimer > 0) hurtTimer -= dt;

        // Check if on rail
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y - 0.1f);
        int bz = (int) Math.floor(z);

        int blockBelow = (by >= 0 && by < WorldConstants.WORLD_HEIGHT) ? world.getBlock(bx, by, bz) : 0;
        onRail = Blocks.isRail(blockBelow);
        onPoweredRail = (blockBelow == Blocks.POWERED_RAIL.id());

        if (onRail) {
            updateOnRail(dt, world, bx, by, bz);
        } else {
            // Off rail — regular physics
            vy -= GRAVITY * dt;
            moveWithCollision(dt, world);
        }
    }

    private void updateOnRail(float dt, World world, int bx, int by, int bz) {
        // Handle powered rail boost/brake
        if (onPoweredRail) {
            boolean isPowered = (redstoneSystem != null && redstoneSystem.isBlockPowered(world, bx, by, bz));

            if (isPowered) {
                // Powered: accelerate the minecart
                if (Math.abs(railSpeed) < 0.1f) {
                    // Give initial launch if nearly stationary
                    // Try to guess direction from mounted player or last travel dir
                    railSpeed = (railSpeed >= 0) ? POWERED_RAIL_LAUNCH : -POWERED_RAIL_LAUNCH;
                } else {
                    // Accelerate in current direction
                    float sign = Math.signum(railSpeed);
                    railSpeed += sign * POWERED_RAIL_ACCEL * dt;
                }
            } else {
                // Unpowered powered rail: brake
                if (Math.abs(railSpeed) > 0.1f) {
                    float sign = Math.signum(railSpeed);
                    railSpeed -= sign * BRAKE_DECEL * dt;
                    // Don't reverse direction from braking
                    if (Math.signum(railSpeed) != sign) railSpeed = 0;
                } else {
                    railSpeed = 0;
                }
            }
        }

        // Apply rail friction (less friction on powered rails)
        if (!onPoweredRail) {
            railSpeed *= RAIL_FRICTION;
        } else {
            railSpeed *= 0.998f; // Very low friction on powered rails
        }

        // Check for slope (rail block with a block below that creates height diff)
        int nextX = bx, nextZ = bz;
        switch (travelDir) {
            case 0 -> nextX++;
            case 1 -> nextZ++;
            case 2 -> nextX--;
            case 3 -> nextZ--;
        }

        // Slope detection: if there's a rail above in the next position, go uphill
        boolean slopeUp = by + 1 < WorldConstants.WORLD_HEIGHT &&
                          Blocks.isRail(world.getBlock(nextX, by + 1, nextZ));
        boolean slopeDown = by - 1 >= 0 &&
                            Blocks.isRail(world.getBlock(nextX, by - 1, nextZ));

        if (slopeDown) {
            railSpeed += SLOPE_BOOST * dt; // Accelerate downhill
        } else if (slopeUp) {
            railSpeed -= SLOPE_BOOST * dt * 0.5f; // Decelerate uphill
        }

        // Clamp speed (higher cap on powered rails)
        float maxSpeed = onPoweredRail ? POWERED_RAIL_MAX_SPEED : RAIL_SPEED;
        railSpeed = Math.max(-maxSpeed, Math.min(maxSpeed, railSpeed));

        // Move along rail direction
        float moveAmount = railSpeed * dt;
        switch (travelDir) {
            case 0 -> x += moveAmount;
            case 1 -> z += moveAmount;
            case 2 -> x -= moveAmount;
            case 3 -> z -= moveAmount;
        }

        // Snap to rail center (perpendicular axis)
        if (travelDir == 0 || travelDir == 2) {
            z = bz + 0.5f; // Center on Z
        } else {
            x = bx + 0.5f; // Center on X
        }

        // Snap Y to rail top
        y = by + 0.1f;

        // Check if still on rail at new position
        int newBx = (int) Math.floor(x);
        int newBz = (int) Math.floor(z);

        if (newBx != bx || newBz != bz) {
            // Entered a new block
            boolean newRail = Blocks.isRail(world.getBlock(newBx, by, newBz));
            boolean newRailUp = by + 1 < WorldConstants.WORLD_HEIGHT &&
                                Blocks.isRail(world.getBlock(newBx, by + 1, newBz));
            boolean newRailDown = by - 1 >= 0 &&
                                  Blocks.isRail(world.getBlock(newBx, by - 1, newBz));

            if (!newRail && !newRailUp && !newRailDown) {
                // Try to find rail in adjacent directions (curve handling)
                if (tryTurn(world, newBx, by, newBz)) {
                    // Successfully turned
                } else {
                    // No rail ahead — stop
                    railSpeed = 0;
                    // Snap back to last rail center
                    x = bx + 0.5f;
                    z = bz + 0.5f;
                }
            } else if (newRailUp) {
                // Go up slope
                y = by + 1 + 0.1f;
            } else if (newRailDown && !newRail) {
                // Go down slope
                y = by - 1 + 0.1f;
            }
        }

        // If speed is near zero, stop
        if (Math.abs(railSpeed) < 0.05f && !onPoweredRail) {
            railSpeed = 0;
        }
    }

    /**
     * Try to turn at an intersection/curve. Look for rails perpendicular to current direction.
     */
    private boolean tryTurn(World world, int bx, int by, int bz) {
        // Try perpendicular directions
        int[] tryDirs;
        if (travelDir == 0 || travelDir == 2) {
            // Currently moving X, try Z directions
            tryDirs = new int[]{1, 3};
        } else {
            // Currently moving Z, try X directions
            tryDirs = new int[]{0, 2};
        }

        for (int dir : tryDirs) {
            int testX = bx, testZ = bz;
            switch (dir) {
                case 0 -> testX++;
                case 1 -> testZ++;
                case 2 -> testX--;
                case 3 -> testZ--;
            }
            if (Blocks.isRail(world.getBlock(testX, by, testZ))) {
                travelDir = dir;
                float speed = Math.abs(railSpeed);
                railSpeed = speed; // Keep magnitude, new direction
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDeath(ItemEntityManager itemManager) {
        itemManager.spawnDrop(Blocks.MINECART_ITEM.id(), 1, x, y, z);
    }

    // ---- Riding ----

    public boolean isMounted() { return mounted; }

    public void mount() {
        mounted = true;
        System.out.println("[Minecart] Player mounted minecart");
    }

    public void dismount() {
        mounted = false;
        System.out.println("[Minecart] Player dismounted minecart");
    }

    /**
     * Push the minecart in a direction. Used when player right-clicks without mounting.
     */
    public void push(float pushX, float pushZ) {
        // Determine which direction to push based on rail orientation
        if (onRail) {
            if (travelDir == 0 || travelDir == 2) {
                railSpeed = pushX > 0 ? PUSH_SPEED : -PUSH_SPEED;
                travelDir = pushX > 0 ? 0 : 2;
            } else {
                railSpeed = pushZ > 0 ? PUSH_SPEED : -PUSH_SPEED;
                travelDir = pushZ > 0 ? 1 : 3;
            }
        } else {
            vx = pushX * PUSH_SPEED;
            vz = pushZ * PUSH_SPEED;
        }
    }

    public boolean isOnRail() { return onRail; }
    public boolean isOnPoweredRail() { return onPoweredRail; }
    public float getRailSpeed() { return railSpeed; }

    @Override
    public boolean shouldDespawn(Player player) {
        return false; // Minecarts don't despawn
    }
}
