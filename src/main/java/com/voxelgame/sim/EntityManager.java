package com.voxelgame.sim;

import com.voxelgame.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages all mob entities in the world.
 * Handles updating, death processing, despawning, and entity raycast.
 */
public class EntityManager {

    /** Maximum total entities (performance cap). */
    public static final int MAX_ENTITIES = 50;

    private final List<Entity> entities = new ArrayList<>();

    /**
     * Add an entity to the world.
     */
    public void addEntity(Entity entity) {
        if (entities.size() >= MAX_ENTITIES) {
            // Remove oldest entity to make room
            Entity oldest = null;
            float oldestAge = -1;
            for (Entity e : entities) {
                if (e.getAge() > oldestAge) {
                    oldestAge = e.getAge();
                    oldest = e;
                }
            }
            if (oldest != null) {
                entities.remove(oldest);
            }
        }
        entities.add(entity);
    }

    /**
     * Update all entities: AI, physics, death, despawning.
     */
    public void update(float dt, World world, Player player, ItemEntityManager itemManager) {
        Iterator<Entity> iter = entities.iterator();
        while (iter.hasNext()) {
            Entity entity = iter.next();

            // Update AI and physics
            entity.update(dt, world, player);

            // Handle death
            if (entity.isDead()) {
                entity.onDeath(itemManager);
                iter.remove();
                continue;
            }

            // Despawn if too far from player
            if (entity.shouldDespawn(player)) {
                iter.remove();
            }
        }
    }

    /**
     * Raycast against all entities. Returns the closest entity hit within maxDist,
     * or null if no entity was hit.
     *
     * @param origin    ray origin (player eye position)
     * @param direction ray direction (player look direction)
     * @param maxDist   maximum hit distance
     * @return closest hit entity, or null
     */
    public Entity raycastEntity(Vector3f origin, Vector3f direction, float maxDist) {
        Entity closest = null;
        float closestDist = maxDist;

        for (Entity entity : entities) {
            if (entity.isDead()) continue;

            float t = entity.rayIntersect(origin, direction);
            if (t >= 0 && t < closestDist) {
                closestDist = t;
                closest = entity;
            }
        }

        return closest;
    }

    /** Get all entities (for rendering). */
    public List<Entity> getEntities() {
        return entities;
    }

    /** Get total entity count. */
    public int getEntityCount() {
        return entities.size();
    }

    /** Count entities of a specific type. */
    public int countType(EntityType type) {
        int count = 0;
        for (Entity e : entities) {
            if (e.getType() == type && !e.isDead()) count++;
        }
        return count;
    }

    /** Clear all entities. */
    public void clear() {
        entities.clear();
    }
}
