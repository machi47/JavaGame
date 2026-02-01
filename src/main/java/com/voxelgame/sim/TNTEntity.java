package com.voxelgame.sim;

import com.voxelgame.world.Block;
import com.voxelgame.world.Blocks;
import com.voxelgame.world.Lighting;
import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TNT entity — ignited TNT block that falls, then explodes after a 4-second fuse.
 * Explosion destroys blocks in a 3-block radius and deals damage to the player.
 * Chain reaction: detonation ignites nearby TNT blocks.
 */
public class TNTEntity extends Entity {

    /** Fuse time in seconds. */
    private static final float FUSE_TIME = 4.0f;
    /** Explosion radius in blocks. */
    private static final int EXPLOSION_RADIUS = 3;
    /** Damage to player at ground zero. */
    private static final float MAX_DAMAGE = 20.0f;
    /** Maximum distance for player damage. */
    private static final float DAMAGE_RADIUS = 5.0f;

    private float fuseTimer;
    private boolean exploded = false;

    /** Reference to world — needed for explosion to destroy blocks. */
    private World worldRef;
    /** Callback for rebuilding chunks after explosion. */
    private ExplosionCallback callback;

    /** Blocks destroyed by the explosion (for mesh rebuilding). */
    private final Set<ChunkPos> affectedChunks = new HashSet<>();

    public interface ExplosionCallback {
        void onBlockDestroyed(int x, int y, int z);
        void rebuildChunks(Set<ChunkPos> chunks);
    }

    public TNTEntity(float x, float y, float z) {
        super(EntityType.TNT, x, y, z, 0.49f, 0.98f, 1.0f);
        this.fuseTimer = FUSE_TIME;
        // Small upward pop on ignition
        this.vy = 4.0f;
    }

    public void setWorldRef(World world) {
        this.worldRef = world;
    }

    public void setExplosionCallback(ExplosionCallback callback) {
        this.callback = callback;
    }

    @Override
    public void update(float dt, World world, Player player) {
        age += dt;
        this.worldRef = world;

        // Physics: gravity and collision
        moveWithCollision(dt, world);

        // Fuse countdown
        fuseTimer -= dt;

        if (fuseTimer <= 0 && !exploded) {
            explode(world, player);
        }
    }

    /**
     * Perform the explosion: destroy blocks, damage player, chain-react TNT.
     */
    private void explode(World world, Player player) {
        exploded = true;
        dead = true;

        System.out.printf("[TNT] Explosion at (%.1f, %.1f, %.1f)!%n", x, y, z);

        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        int cz = (int) Math.floor(z);

        // Collect TNT blocks that should chain-react
        List<int[]> chainTNT = new ArrayList<>();

        // Destroy blocks in sphere
        for (int dx = -EXPLOSION_RADIUS; dx <= EXPLOSION_RADIUS; dx++) {
            for (int dy = -EXPLOSION_RADIUS; dy <= EXPLOSION_RADIUS; dy++) {
                for (int dz = -EXPLOSION_RADIUS; dz <= EXPLOSION_RADIUS; dz++) {
                    float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > EXPLOSION_RADIUS) continue;

                    int bx = cx + dx;
                    int by = cy + dy;
                    int bz = cz + dz;

                    if (by < 0 || by >= WorldConstants.WORLD_HEIGHT) continue;

                    int blockId = world.getBlock(bx, by, bz);
                    if (blockId == 0) continue; // air

                    Block block = Blocks.get(blockId);
                    if (block.isUnbreakable()) continue; // bedrock, water

                    // Check for TNT chain reaction
                    if (blockId == Blocks.TNT.id()) {
                        chainTNT.add(new int[]{bx, by, bz});
                    }

                    // Destroy block
                    world.setBlock(bx, by, bz, 0);
                    Set<ChunkPos> affected = Lighting.onBlockRemoved(world, bx, by, bz);
                    affectedChunks.addAll(affected);

                    if (callback != null) {
                        callback.onBlockDestroyed(bx, by, bz);
                    }

                    // Random chance to drop items (50%)
                    // (Drops are handled by the caller if needed)
                }
            }
        }

        // Rebuild chunks
        if (callback != null) {
            callback.rebuildChunks(affectedChunks);
        }

        // Damage player
        if (player != null && !player.isDead()) {
            float px = player.getPosition().x;
            float py = player.getPosition().y - Player.EYE_HEIGHT;
            float pz = player.getPosition().z;

            float pdx = px - x;
            float pdy = py - y;
            float pdz = pz - z;
            float pDist = (float) Math.sqrt(pdx * pdx + pdy * pdy + pdz * pdz);

            if (pDist < DAMAGE_RADIUS) {
                float damageFactor = 1.0f - (pDist / DAMAGE_RADIUS);
                float damage = MAX_DAMAGE * damageFactor;
                player.damage(damage, DamageSource.EXPLOSION);
                System.out.printf("[TNT] Player took %.1f explosion damage (dist=%.1f)%n", damage, pDist);
            }
        }

        // Chain reaction: spawn new TNT entities for nearby TNT blocks
        for (int[] tnt : chainTNT) {
            // The TNT block was already removed; spawn a new TNT entity
            // Add small random delay via fuse offset
            TNTEntity chain = new TNTEntity(tnt[0] + 0.5f, tnt[1], tnt[2] + 0.5f);
            chain.fuseTimer = 0.5f + random.nextFloat() * 1.5f; // 0.5-2.0 second delay
            chain.setWorldRef(world);
            chain.setExplosionCallback(callback);

            // Add to entity manager via a static list that the caller processes
            pendingChainTNT.add(chain);
        }
    }

    /** Pending chain-reaction TNT entities to be added by the entity manager. */
    private static final List<TNTEntity> pendingChainTNT = new ArrayList<>();

    /** Get and clear pending chain-reaction TNT entities. */
    public static List<TNTEntity> drainPendingChainTNT() {
        if (pendingChainTNT.isEmpty()) return List.of();
        List<TNTEntity> result = new ArrayList<>(pendingChainTNT);
        pendingChainTNT.clear();
        return result;
    }

    // ---- Getters ----

    public float getFuseTimer() { return fuseTimer; }
    public boolean hasExploded() { return exploded; }

    /** Blink state for rendering (flashes white as fuse gets shorter). */
    public boolean isBlinking() {
        if (fuseTimer < 1.0f) return ((int)(fuseTimer * 10)) % 2 == 0;
        if (fuseTimer < 2.0f) return ((int)(fuseTimer * 5)) % 2 == 0;
        return ((int)(fuseTimer * 2)) % 2 == 0;
    }

    @Override
    public void onDeath(ItemEntityManager itemManager) {
        // TNT doesn't drop items when it explodes
    }

    @Override
    public boolean shouldDespawn(Player player) {
        return false; // TNT doesn't despawn
    }
}
