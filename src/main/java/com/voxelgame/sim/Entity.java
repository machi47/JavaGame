package com.voxelgame.sim;

import com.voxelgame.world.Blocks;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Base class for all mob entities (pigs, zombies, etc.).
 * Position is at feet level. Bounding box extends from (x-halfWidth, y, z-halfWidth)
 * to (x+halfWidth, y+height, z+halfWidth).
 */
public abstract class Entity {

    // ---- Physics constants ----
    protected static final float GRAVITY = 32.0f;
    protected static final float MAX_FALL_SPEED = 40.0f;

    // ---- Despawn ----
    protected static final float DESPAWN_DISTANCE_SQ = 128.0f * 128.0f;

    // ---- Damage cooldown ----
    protected static final float HURT_COOLDOWN = 0.5f;

    // ---- Position (feet level) ----
    protected float x, y, z;

    // ---- Velocity ----
    protected float vx, vy, vz;

    // ---- Bounding box ----
    protected final float halfWidth;
    protected final float height;

    // ---- Health ----
    protected float health;
    protected final float maxHealth;
    protected boolean dead = false;

    // ---- Entity identity ----
    protected final EntityType type;

    // ---- Hurt timer (invulnerability frames) ----
    protected float hurtTimer = 0;

    // ---- Facing direction (yaw in degrees, 0 = +Z) ----
    protected float yaw = 0;

    // ---- Age ----
    protected float age = 0;

    // ---- Ground state ----
    protected boolean onGround = false;

    // ---- Random for AI ----
    protected final Random random = new Random();

    protected Entity(EntityType type, float x, float y, float z,
                     float halfWidth, float height, float maxHealth) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.halfWidth = halfWidth;
        this.height = height;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
        this.yaw = random.nextFloat() * 360.0f;
    }

    // ================================================================
    // Abstract methods — subclasses define behavior
    // ================================================================

    /** Update AI and physics each tick. */
    public abstract void update(float dt, World world, Player player);

    /** Called when entity dies — spawn drops. */
    public abstract void onDeath(ItemEntityManager itemManager);

    // ================================================================
    // Damage
    // ================================================================

    /**
     * Apply damage to this entity with directional knockback.
     *
     * @param amount     raw damage
     * @param knockbackX horizontal knockback X
     * @param knockbackZ horizontal knockback Z
     */
    public void damage(float amount, float knockbackX, float knockbackZ) {
        if (dead || hurtTimer > 0) return;

        health -= amount;
        hurtTimer = HURT_COOLDOWN;

        // Apply knockback
        vx += knockbackX;
        vy += 5.0f; // upward pop
        vz += knockbackZ;

        System.out.printf("[Mob] %s took %.1f damage (HP: %.1f/%.1f)%n",
                type, amount, Math.max(0, health), maxHealth);

        if (health <= 0) {
            health = 0;
            dead = true;
        }
    }

    // ================================================================
    // Physics — simple AABB collision with voxel world
    // ================================================================

    /**
     * Apply gravity, friction, and move with collision detection.
     * Entities use a simplified collision compared to the player.
     */
    protected void moveWithCollision(float dt, World world) {
        // ---- Gravity ----
        vy -= GRAVITY * dt;
        if (vy < -MAX_FALL_SPEED) vy = -MAX_FALL_SPEED;

        // ---- Friction ----
        float friction = onGround ? 0.85f : 0.98f;
        vx *= friction;
        vz *= friction;
        if (Math.abs(vx) < 0.01f) vx = 0;
        if (Math.abs(vz) < 0.01f) vz = 0;

        float dx = vx * dt;
        float dy = vy * dt;
        float dz = vz * dt;

        // ---- Y axis (ground / ceiling) ----
        float ny = y + dy;
        onGround = false;

        if (dy < 0) {
            // Falling — check ground
            int by = (int) Math.floor(ny);
            if (by >= 0) {
                boolean hitGround = false;
                int bx0 = (int) Math.floor(x - halfWidth + 0.01f);
                int bx1 = (int) Math.floor(x + halfWidth - 0.01f);
                int bz0 = (int) Math.floor(z - halfWidth + 0.01f);
                int bz1 = (int) Math.floor(z + halfWidth - 0.01f);

                for (int bx = bx0; bx <= bx1; bx++) {
                    for (int bz = bz0; bz <= bz1; bz++) {
                        if (isSolid(world, bx, by, bz)) {
                            hitGround = true;
                        }
                    }
                }

                if (hitGround) {
                    ny = by + 1.0f;
                    vy = 0;
                    onGround = true;
                }
            }
        } else if (dy > 0) {
            // Jumping — check ceiling
            int headY = (int) Math.floor(ny + height);
            if (headY >= 0 && headY < WorldConstants.WORLD_HEIGHT) {
                int bx0 = (int) Math.floor(x - halfWidth + 0.01f);
                int bx1 = (int) Math.floor(x + halfWidth - 0.01f);
                int bz0 = (int) Math.floor(z - halfWidth + 0.01f);
                int bz1 = (int) Math.floor(z + halfWidth - 0.01f);

                for (int bx = bx0; bx <= bx1; bx++) {
                    for (int bz = bz0; bz <= bz1; bz++) {
                        if (isSolid(world, bx, headY, bz)) {
                            ny = headY - height;
                            vy = 0;
                        }
                    }
                }
            }
        }
        y = ny;

        // ---- X axis ----
        float nx = x + dx;
        if (dx != 0) {
            int checkBx = dx > 0
                    ? (int) Math.floor(nx + halfWidth)
                    : (int) Math.floor(nx - halfWidth);
            boolean xBlocked = false;
            int by0 = (int) Math.floor(y + 0.01f);
            int by1 = (int) Math.floor(y + height - 0.01f);
            int bz0 = (int) Math.floor(z - halfWidth + 0.01f);
            int bz1 = (int) Math.floor(z + halfWidth - 0.01f);

            for (int by = by0; by <= by1 && !xBlocked; by++) {
                for (int bz = bz0; bz <= bz1 && !xBlocked; bz++) {
                    if (isSolid(world, checkBx, by, bz)) {
                        xBlocked = true;
                    }
                }
            }

            if (!xBlocked) x = nx;
            else vx = 0;
        }

        // ---- Z axis ----
        float nz = z + dz;
        if (dz != 0) {
            int checkBz = dz > 0
                    ? (int) Math.floor(nz + halfWidth)
                    : (int) Math.floor(nz - halfWidth);
            boolean zBlocked = false;
            int by0 = (int) Math.floor(y + 0.01f);
            int by1 = (int) Math.floor(y + height - 0.01f);
            int bx0 = (int) Math.floor(x - halfWidth + 0.01f);
            int bx1 = (int) Math.floor(x + halfWidth - 0.01f);

            for (int by = by0; by <= by1 && !zBlocked; by++) {
                for (int bx = bx0; bx <= bx1 && !zBlocked; bx++) {
                    if (isSolid(world, bx, by, checkBz)) {
                        zBlocked = true;
                    }
                }
            }

            if (!zBlocked) z = nz;
            else vz = 0;
        }

        // ---- Void check ----
        if (y < -64) dead = true;

        // ---- Hurt timer countdown ----
        if (hurtTimer > 0) hurtTimer -= dt;
    }

    // ================================================================
    // Ray-AABB intersection (for player attack raycast)
    // ================================================================

    /**
     * Test ray-AABB intersection. Returns distance along ray to hit point,
     * or -1 if no intersection.
     */
    public float rayIntersect(Vector3f origin, Vector3f dir) {
        float minX = x - halfWidth, maxX = x + halfWidth;
        float minY = y, maxY = y + height;
        float minZ = z - halfWidth, maxZ = z + halfWidth;

        float tmin = Float.NEGATIVE_INFINITY;
        float tmax = Float.POSITIVE_INFINITY;

        // X slab
        if (Math.abs(dir.x) > 1e-8f) {
            float t1 = (minX - origin.x) / dir.x;
            float t2 = (maxX - origin.x) / dir.x;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
        } else if (origin.x < minX || origin.x > maxX) {
            return -1;
        }

        // Y slab
        if (Math.abs(dir.y) > 1e-8f) {
            float t1 = (minY - origin.y) / dir.y;
            float t2 = (maxY - origin.y) / dir.y;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
        } else if (origin.y < minY || origin.y > maxY) {
            return -1;
        }

        // Z slab
        if (Math.abs(dir.z) > 1e-8f) {
            float t1 = (minZ - origin.z) / dir.z;
            float t2 = (maxZ - origin.z) / dir.z;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
        } else if (origin.z < minZ || origin.z > maxZ) {
            return -1;
        }

        if (tmin > tmax || tmax < 0) return -1;
        return tmin >= 0 ? tmin : tmax;
    }

    // ================================================================
    // Player collision check
    // ================================================================

    /**
     * Check if this entity's AABB overlaps with the player's AABB.
     */
    public boolean isCollidingWithPlayer(Player player) {
        Vector3f pPos = player.getPosition();
        float px = pPos.x;
        float py = pPos.y - Player.EYE_HEIGHT; // player feet
        float pz = pPos.z;
        float phw = Player.HALF_WIDTH;
        float ph = Player.HEIGHT;

        return (x - halfWidth < px + phw) && (x + halfWidth > px - phw) &&
               (y < py + ph) && (y + height > py) &&
               (z - halfWidth < pz + phw) && (z + halfWidth > pz - phw);
    }

    // ================================================================
    // Distance check (for despawning)
    // ================================================================

    /**
     * Check if this entity should despawn (too far from player).
     */
    public boolean shouldDespawn(Player player) {
        Vector3f pPos = player.getPosition();
        float dx = x - pPos.x;
        float dz = z - pPos.z;
        return (dx * dx + dz * dz) > DESPAWN_DISTANCE_SQ;
    }

    // ================================================================
    // Helpers
    // ================================================================

    protected boolean isSolid(World world, int bx, int by, int bz) {
        if (by < 0 || by >= WorldConstants.WORLD_HEIGHT) return false;
        return Blocks.get(world.getBlock(bx, by, bz)).solid();
    }

    /**
     * Attempt to jump (if on ground).
     */
    protected void jump() {
        if (onGround) {
            vy = 9.0f;
            onGround = false;
        }
    }

    // ================================================================
    // Getters
    // ================================================================

    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public float getHalfWidth() { return halfWidth; }
    public float getHeight() { return height; }
    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public boolean isDead() { return dead; }
    public EntityType getType() { return type; }
    public float getYaw() { return yaw; }
    public float getAge() { return age; }
    public float getHurtTimer() { return hurtTimer; }
    public boolean isOnGround() { return onGround; }
}
