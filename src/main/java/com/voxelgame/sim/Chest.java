package com.voxelgame.sim;

import com.voxelgame.world.Blocks;

/**
 * Chest tile entity — stores a 27-slot inventory at a specific world position.
 * Chests are "tile entities" — blocks with extra data (inventory).
 */
public class Chest {

    public static final int CHEST_SIZE = 27;

    /** World position of the chest block. */
    private final int x, y, z;

    /** The chest's inventory (27 slots). */
    private final Inventory.ItemStack[] slots = new Inventory.ItemStack[CHEST_SIZE];

    public Chest(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // ---- Inventory operations ----

    public Inventory.ItemStack getSlot(int index) {
        if (index < 0 || index >= CHEST_SIZE) return null;
        return slots[index];
    }

    public void setSlot(int index, Inventory.ItemStack stack) {
        if (index < 0 || index >= CHEST_SIZE) return;
        slots[index] = stack;
    }

    /** Swap two slots (for drag-and-drop). */
    public void swapSlots(int a, int b) {
        if (a < 0 || a >= CHEST_SIZE || b < 0 || b >= CHEST_SIZE) return;
        Inventory.ItemStack temp = slots[a];
        slots[a] = slots[b];
        slots[b] = temp;
    }

    /**
     * Add an item to the chest. Returns leftover count.
     */
    public int addItem(int blockId, int count) {
        if (blockId <= 0 || count <= 0) return 0;
        int remaining = count;

        // Try to stack first
        for (int i = 0; i < CHEST_SIZE && remaining > 0; i++) {
            if (slots[i] != null && slots[i].getBlockId() == blockId
                && !slots[i].isFull() && !slots[i].hasDurability()) {
                remaining = slots[i].add(remaining);
            }
        }
        // Then empty slots
        for (int i = 0; i < CHEST_SIZE && remaining > 0; i++) {
            if (slots[i] == null || slots[i].isEmpty()) {
                int toPlace = Math.min(remaining, Inventory.MAX_STACK);
                slots[i] = new Inventory.ItemStack(blockId, toPlace);
                remaining -= toPlace;
            }
        }
        return remaining;
    }

    /**
     * Drop all items from the chest (returns array of non-null stacks).
     */
    public Inventory.ItemStack[] dropAll() {
        Inventory.ItemStack[] drops = new Inventory.ItemStack[CHEST_SIZE];
        for (int i = 0; i < CHEST_SIZE; i++) {
            if (slots[i] != null && !slots[i].isEmpty()) {
                drops[i] = slots[i].copy();
                slots[i] = null;
            }
        }
        return drops;
    }

    /**
     * Check if the chest is completely empty.
     */
    public boolean isEmpty() {
        for (Inventory.ItemStack slot : slots) {
            if (slot != null && !slot.isEmpty()) return false;
        }
        return true;
    }

    // ---- Position ----

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    /**
     * Check if this chest is at the given position.
     */
    public boolean isAt(int bx, int by, int bz) {
        return x == bx && y == by && z == bz;
    }

    // ---- Serialization helpers ----

    /**
     * Serialize chest inventory to a compact int array.
     * Format: [blockId, count, blockId, count, ...] for 27 slots.
     * Empty slots use blockId=0, count=0.
     */
    public int[] serialize() {
        int[] data = new int[CHEST_SIZE * 2];
        for (int i = 0; i < CHEST_SIZE; i++) {
            if (slots[i] != null && !slots[i].isEmpty()) {
                data[i * 2] = slots[i].getBlockId();
                data[i * 2 + 1] = slots[i].getCount();
            }
        }
        return data;
    }

    /**
     * Deserialize chest inventory from an int array.
     */
    public void deserialize(int[] data) {
        if (data == null || data.length < CHEST_SIZE * 2) return;
        for (int i = 0; i < CHEST_SIZE; i++) {
            int blockId = data[i * 2];
            int count = data[i * 2 + 1];
            if (blockId > 0 && count > 0) {
                slots[i] = new Inventory.ItemStack(blockId, count);
            } else {
                slots[i] = null;
            }
        }
    }
}
