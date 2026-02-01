package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;

/**
 * Minecart entity — rideable, moves on rail blocks.
 * Follows rail paths automatically, powered by slopes or player push.
 * Right-click to mount/dismount.
 */
public class Minecart extends Entity {

    private static final float CART_HALF_WIDTH = 0.45f;
    private static final float CART_HEIGHT = 0.6f;
    private static final float CART_MAX_HEALTH = 20.0f;

    /** Speed on rails. */
    private static final float RAIL_SPEED = 8.0f;
    /** Slope boost. */
    private static final float SLOPE_BOOST = 4.0f;
    /** Rail friction (slow deceleration). */
    private static final float RAIL_FRICTION = 0.99f;
    /** Push speed when player right-clicks while not mounting. */
    private static final float PUSH_SPEED = 5.0f;

    /** Whether a player is riding. */
    private boolean mounted = false;

    /** Direction of travel: 0=+X, 1=+Z, 2=-X, 3=-Z */
    private int travelDir = 1;

    /** Speed along the rail (signed, direction determined by travelDir). */
    private float railSpeed = 0;

    /** Whether currently on a rail block. */
    private boolean onRail = false;

    public Minecart(float x, float y, float z) {
        super(EntityType.MINECART, x, y, z, CART_HALF_WIDTH, CART_HEIGHT, CART_MAX_HEALTH);
    }

    @Override
    public void update(float dt, World world, Player player) {
        age += dt;
        if (hurtTimer > 0) hurtTimer -= dt;

        // Check if on rail
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y - 0.1f);
        int bz = (int) Math.floor(z);
        onRail = (by >= 0 && by < WorldConstants.WORLD_HEIGHT &&
                  world.getBlock(bx, by, bz) == Blocks.RAIL.id());

        if (onRail) {
            updateOnRail(dt, world, bx, by, bz);
        } else {
            // Off rail — regular physics
            vy -= GRAVITY * dt;
            moveWithCollision(dt, world);
        }
    }

    private void updateOnRail(float dt, World world, int bx, int by, int bz) {
        // Apply rail friction
        railSpeed *= RAIL_FRICTION;

        // Check for slope (rail block with a block below that creates height diff)
        // Look for rails above or below in travel direction to detect slopes
        int nextX = bx, nextZ = bz;
        switch (travelDir) {
            case 0 -> nextX++;
            case 1 -> nextZ++;
            case 2 -> nextX--;
            case 3 -> nextZ--;
        }

        // Slope detection: if there's a rail above in the next position, go uphill
        boolean slopeUp = by + 1 < WorldConstants.WORLD_HEIGHT &&
                          world.getBlock(nextX, by + 1, nextZ) == Blocks.RAIL.id();
        boolean slopeDown = by - 1 >= 0 &&
                            world.getBlock(nextX, by - 1, nextZ) == Blocks.RAIL.id();

        if (slopeDown) {
            railSpeed += SLOPE_BOOST * dt; // Accelerate downhill
        } else if (slopeUp) {
            railSpeed -= SLOPE_BOOST * dt * 0.5f; // Decelerate uphill
        }

        // Clamp speed
        railSpeed = Math.max(-RAIL_SPEED, Math.min(RAIL_SPEED, railSpeed));

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
            boolean newRail = world.getBlock(newBx, by, newBz) == Blocks.RAIL.id();
            boolean newRailUp = by + 1 < WorldConstants.WORLD_HEIGHT &&
                                world.getBlock(newBx, by + 1, newBz) == Blocks.RAIL.id();
            boolean newRailDown = by - 1 >= 0 &&
                                  world.getBlock(newBx, by - 1, newBz) == Blocks.RAIL.id();

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
        if (Math.abs(railSpeed) < 0.05f) {
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
            if (world.getBlock(testX, by, testZ) == Blocks.RAIL.id()) {
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
    public float getRailSpeed() { return railSpeed; }

    @Override
    public boolean shouldDespawn(Player player) {
        return false; // Minecarts don't despawn
    }
}
