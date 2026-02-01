package com.voxelgame.sim;

import com.voxelgame.world.Blocks;

/**
 * Furnace tile entity. Has input, fuel, and output slots.
 * Smelts items using fuel over time.
 */
public class Furnace {

    private final int x, y, z;

    // Slot contents (blockId, 0 = empty)
    private int inputId = 0;
    private int inputCount = 0;
    private int fuelId = 0;
    private int fuelCount = 0;
    private int outputId = 0;
    private int outputCount = 0;

    // Smelting state
    private float smeltProgress = 0;       // 0.0 - 1.0
    private float fuelRemaining = 0;       // ticks of fuel left
    private float currentFuelTotal = 0;    // total ticks the current fuel lasts
    private boolean active = false;

    // 200 ticks to smelt one item (at 20 ticks per second, that's 10 seconds)
    private static final int SMELT_TICKS = 200;
    private static final int MAX_STACK = 64;

    public Furnace(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // Getters
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public int getInputId() { return inputId; }
    public int getInputCount() { return inputCount; }
    public int getFuelId() { return fuelId; }
    public int getFuelCount() { return fuelCount; }
    public int getOutputId() { return outputId; }
    public int getOutputCount() { return outputCount; }
    public float getSmeltProgress() { return smeltProgress; }
    public float getFuelRemaining() { return fuelRemaining; }
    public float getCurrentFuelTotal() { return currentFuelTotal; }
    public boolean isActive() { return active; }

    // Position check
    public boolean isAt(int x, int y, int z) {
        return this.x == x && this.y == y && this.z == z;
    }

    // Setters for UI interaction
    public void setInput(int id, int count) { this.inputId = id; this.inputCount = count; }
    public void setFuel(int id, int count) { this.fuelId = id; this.fuelCount = count; }
    public void setOutput(int id, int count) { this.outputId = id; this.outputCount = count; }

    /**
     * Called every game tick (50ms intervals).
     */
    public void tick() {
        // Check if we can smelt
        int resultId = SmeltingRecipe.getResult(inputId);

        if (resultId == 0 || inputCount <= 0) {
            smeltProgress = 0;
            active = false;
            return;
        }

        // Check if output slot can accept the result
        if (outputId != 0 && outputId != resultId) {
            active = false;
            return;
        }
        if (outputCount >= MAX_STACK) {
            active = false;
            return;
        }

        // Consume fuel if needed
        if (fuelRemaining <= 0) {
            int fuelValue = SmeltingRecipe.getFuelValue(fuelId);
            if (fuelValue > 0 && fuelCount > 0) {
                fuelCount--;
                if (fuelCount <= 0) fuelId = 0;
                fuelRemaining = fuelValue;
                currentFuelTotal = fuelValue;
            } else {
                // No fuel available
                active = false;
                smeltProgress = 0;
                return;
            }
        }

        active = true;
        fuelRemaining--;

        // Progress smelting
        smeltProgress += 1.0f / SMELT_TICKS;

        if (smeltProgress >= 1.0f) {
            // Complete one smelt cycle
            smeltProgress = 0;
            inputCount--;
            if (inputCount <= 0) inputId = 0;

            if (outputId == 0) {
                outputId = resultId;
                outputCount = 1;
            } else {
                outputCount++;
            }
        }
    }

    /**
     * Restore internal smelting state from saved data.
     */
    public void restoreState(float smeltProgress, float fuelRemaining, float currentFuelTotal) {
        this.smeltProgress = smeltProgress;
        this.fuelRemaining = fuelRemaining;
        this.currentFuelTotal = currentFuelTotal;
        this.active = (fuelRemaining > 0);
    }

    /**
     * Check if a given item is a valid fuel.
     */
    public static boolean isFuel(int blockId) {
        return SmeltingRecipe.isFuel(blockId);
    }

    /**
     * Drop all contents as ItemStacks (for when the furnace is broken).
     */
    public Inventory.ItemStack[] dropAll() {
        Inventory.ItemStack[] drops = new Inventory.ItemStack[3];
        if (inputId > 0 && inputCount > 0) {
            drops[0] = new Inventory.ItemStack(inputId, inputCount);
        }
        if (fuelId > 0 && fuelCount > 0) {
            drops[1] = new Inventory.ItemStack(fuelId, fuelCount);
        }
        if (outputId > 0 && outputCount > 0) {
            drops[2] = new Inventory.ItemStack(outputId, outputCount);
        }
        inputId = 0; inputCount = 0;
        fuelId = 0; fuelCount = 0;
        outputId = 0; outputCount = 0;
        return drops;
    }
}
