package com.voxelgame.ui;

import com.voxelgame.render.Shader;
import com.voxelgame.sim.Chest;
import com.voxelgame.sim.Inventory;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Chest inventory screen. Shows chest inventory (27 slots, 3 rows of 9) on top
 * and player inventory (36 slots, 4 rows of 9) on bottom.
 */
public class ChestScreen {

    private static final float SLOT_SIZE = 44.0f;
    private static final float SLOT_GAP = 4.0f;
    private static final float ROW_GAP = 8.0f;
    private static final float SECTION_GAP = 20.0f;
    private static final float PREVIEW_SIZE = 30.0f;
    private static final float BORDER = 2.0f;
    private static final float BG_PADDING = 16.0f;

    private static final float[][] BLOCK_COLORS = {
        {0.0f, 0.0f, 0.0f, 0.0f},
        {0.47f, 0.47f, 0.47f, 1.0f},
        {0.39f, 0.39f, 0.39f, 1.0f},
        {0.53f, 0.38f, 0.26f, 1.0f},
        {0.30f, 0.60f, 0.00f, 1.0f},
        {0.84f, 0.81f, 0.60f, 1.0f},
        {0.51f, 0.49f, 0.49f, 1.0f},
        {0.39f, 0.27f, 0.16f, 1.0f},
        {0.20f, 0.51f, 0.04f, 0.9f},
        {0.12f, 0.31f, 0.78f, 0.6f},
        {0.35f, 0.35f, 0.35f, 1.0f},
        {0.55f, 0.45f, 0.35f, 1.0f},
        {0.75f, 0.65f, 0.20f, 1.0f},
        {0.39f, 0.86f, 1.00f, 1.0f},
        {0.16f, 0.16f, 0.16f, 1.0f},
        {0.95f, 0.55f, 0.50f, 1.0f},
        {0.55f, 0.40f, 0.25f, 1.0f},
        {0.70f, 0.55f, 0.30f, 1.0f},
        {0.60f, 0.45f, 0.25f, 1.0f},
        {0.65f, 0.50f, 0.25f, 1.0f},
        {0.58f, 0.42f, 0.20f, 1.0f},
        {0.55f, 0.40f, 0.18f, 1.0f},
        {0.52f, 0.38f, 0.16f, 1.0f},
        {0.50f, 0.50f, 0.52f, 1.0f},
        {0.48f, 0.48f, 0.50f, 1.0f},
        {0.46f, 0.46f, 0.48f, 1.0f},
        {0.60f, 0.40f, 0.20f, 1.0f},  // 26 CHEST
        {0.45f, 0.45f, 0.45f, 1.0f},  // 27 RAIL
        {0.85f, 0.20f, 0.15f, 1.0f},  // 28 TNT
        {0.55f, 0.35f, 0.15f, 1.0f},  // 29 BOAT
        {0.50f, 0.50f, 0.55f, 1.0f},  // 30 MINECART
    };

    private boolean visible = false;
    private Chest currentChest = null;
    private Inventory.ItemStack heldItem = null;

    private Shader uiShader;
    private int quadVao, quadVbo;
    private BitmapFont font;
    private int sw, sh;
    private float mouseX, mouseY;

    // Layout positions
    private float chestX0, chestY0;  // chest inventory top-left
    private float invX0, invY0;      // player inventory top-left

    // Slot index ranges:
    // 0..26 = chest slots
    // 100..135 = player inventory slots (maps to Inventory slot 0..35)
    private static final int PLAYER_SLOT_BASE = 100;

    public void init(BitmapFont font) {
        this.font = font;
        uiShader = new Shader("shaders/ui.vert", "shaders/ui.frag");

        float[] v = { 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1 };
        quadVao = glGenVertexArrays();
        quadVbo = glGenBuffers();
        glBindVertexArray(quadVao);
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(v.length);
            fb.put(v).flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        }
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * 4, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    public boolean isVisible() { return visible; }
    public boolean isOpen() { return visible; }

    /**
     * Open the chest screen for a specific chest.
     */
    public void open(Chest chest) {
        this.currentChest = chest;
        this.visible = true;
        this.heldItem = null;
    }

    /**
     * Close the chest screen. Returns held items to player inventory.
     */
    public void close(Inventory playerInventory) {
        if (heldItem != null) {
            playerInventory.addItemStack(heldItem);
            heldItem = null;
        }
        visible = false;
        currentChest = null;
    }

    public Chest getCurrentChest() { return currentChest; }

    public void updateMouse(double mx, double my, int screenH) {
        this.mouseX = (float) mx;
        this.mouseY = screenH - (float) my;
    }

    // ---- Click handling ----

    public void handleClick(Inventory playerInventory, double mx, double my,
                            int screenW, int screenH) {
        if (!visible || currentChest == null) return;
        this.sw = screenW;
        this.sh = screenH;
        float clickX = (float) mx;
        float clickY = screenH - (float) my;
        computeLayout();

        int slot = getSlotAt(clickX, clickY);
        if (slot < 0) return;

        if (slot >= 0 && slot < Chest.CHEST_SIZE) {
            handleChestSlotClick(slot);
        } else if (slot >= PLAYER_SLOT_BASE && slot < PLAYER_SLOT_BASE + Inventory.TOTAL_SIZE) {
            handlePlayerSlotClick(playerInventory, slot - PLAYER_SLOT_BASE);
        }
    }

    private void handleChestSlotClick(int chestSlot) {
        Inventory.ItemStack slotItem = currentChest.getSlot(chestSlot);
        if (heldItem == null) {
            if (slotItem != null && !slotItem.isEmpty()) {
                heldItem = slotItem.copy();
                currentChest.setSlot(chestSlot, null);
            }
        } else {
            if (slotItem == null || slotItem.isEmpty()) {
                currentChest.setSlot(chestSlot, heldItem);
                heldItem = null;
            } else if (slotItem.getBlockId() == heldItem.getBlockId()
                       && !slotItem.isFull() && !slotItem.hasDurability() && !heldItem.hasDurability()) {
                int leftover = slotItem.add(heldItem.getCount());
                if (leftover > 0) heldItem.setCount(leftover);
                else heldItem = null;
            } else {
                currentChest.setSlot(chestSlot, heldItem);
                heldItem = slotItem;
            }
        }
    }

    private void handlePlayerSlotClick(Inventory inventory, int invSlot) {
        Inventory.ItemStack slotItem = inventory.getSlot(invSlot);
        if (heldItem == null) {
            if (slotItem != null && !slotItem.isEmpty()) {
                heldItem = slotItem.copy();
                inventory.setSlot(invSlot, null);
            }
        } else {
            if (slotItem == null || slotItem.isEmpty()) {
                inventory.setSlot(invSlot, heldItem);
                heldItem = null;
            } else if (slotItem.getBlockId() == heldItem.getBlockId()
                       && !slotItem.isFull() && !slotItem.hasDurability() && !heldItem.hasDurability()) {
                int leftover = slotItem.add(heldItem.getCount());
                if (leftover > 0) heldItem.setCount(leftover);
                else heldItem = null;
            } else {
                inventory.setSlot(invSlot, heldItem);
                heldItem = slotItem;
            }
        }
    }

    // ---- Layout ----

    private void computeLayout() {
        float gridW = 9 * SLOT_SIZE + 8 * SLOT_GAP;
        float chestH = 3 * SLOT_SIZE + 2 * SLOT_GAP;
        float invH = 4 * SLOT_SIZE + 3 * SLOT_GAP + ROW_GAP;
        float totalH = chestH + SECTION_GAP + invH;

        float startX = (sw - gridW) / 2.0f;
        float startY = (sh - totalH) / 2.0f;

        // Player inventory at bottom
        invX0 = startX;
        invY0 = startY;

        // Chest inventory above player inventory
        chestX0 = startX;
        chestY0 = startY + invH + SECTION_GAP;
    }

    private int getSlotAt(float mx, float my) {
        computeLayout();

        // Chest slots (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = chestX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy = chestY0 + (2 - row) * (SLOT_SIZE + SLOT_GAP);
                if (mx >= sx && mx <= sx + SLOT_SIZE && my >= sy && my <= sy + SLOT_SIZE) {
                    return row * 9 + col;
                }
            }
        }

        // Player inventory (4 rows: hotbar + 3 storage rows)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = invX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy;
                if (row == 0) sy = invY0;
                else sy = invY0 + SLOT_SIZE + ROW_GAP + (row - 1) * (SLOT_SIZE + SLOT_GAP);
                if (mx >= sx && mx <= sx + SLOT_SIZE && my >= sy && my <= sy + SLOT_SIZE) {
                    int invSlot = (row == 0) ? col : (9 + (row - 1) * 9 + col);
                    return PLAYER_SLOT_BASE + invSlot;
                }
            }
        }

        return -1;
    }

    // ---- Rendering ----

    public void render(int screenW, int screenH, Inventory playerInventory) {
        if (!visible || currentChest == null) return;
        this.sw = screenW;
        this.sh = screenH;
        computeLayout();

        uiShader.bind();
        glBindVertexArray(quadVao);

        // Dim background
        fillRect(0, 0, sw, sh, 0.0f, 0.0f, 0.0f, 0.6f);

        // Background panel
        float gridW = 9 * SLOT_SIZE + 8 * SLOT_GAP;
        float chestH = 3 * SLOT_SIZE + 2 * SLOT_GAP;
        float invH = 4 * SLOT_SIZE + 3 * SLOT_GAP + ROW_GAP;
        float totalH = chestH + SECTION_GAP + invH;
        fillRect(invX0 - BG_PADDING, invY0 - BG_PADDING,
                 gridW + BG_PADDING * 2, totalH + BG_PADDING * 2,
                 0.15f, 0.15f, 0.15f, 0.9f);

        // Render chest slots
        renderChestSlots();

        // Render player inventory
        renderPlayerSlots(playerInventory);

        // Render held item at cursor
        if (heldItem != null && !heldItem.isEmpty()) {
            renderItemPreview(mouseX - SLOT_SIZE / 2, mouseY - SLOT_SIZE / 2, heldItem);
        }

        uiShader.unbind();

        // Render text labels
        if (font != null) {
            float chestLabelY = chestY0 + 3 * (SLOT_SIZE + SLOT_GAP) + 4;
            font.drawText("Chest", chestX0, chestLabelY, 2.0f, sw, sh, 0.9f, 0.7f, 0.4f, 1.0f);

            float invLabelY = invY0 + invH + 4;
            font.drawText("Inventory", invX0, invLabelY, 2.0f, sw, sh, 0.9f, 0.9f, 0.9f, 1.0f);

            renderSlotCounts(playerInventory);

            if (heldItem != null && !heldItem.isEmpty() && heldItem.getCount() > 1 && !heldItem.hasDurability()) {
                String cs = String.valueOf(heldItem.getCount());
                float tx = mouseX - SLOT_SIZE / 2 + SLOT_SIZE - 8 * cs.length() - 2;
                float ty = mouseY - SLOT_SIZE / 2 + 2;
                font.drawText(cs, tx + 1, ty + 1, 1.5f, sw, sh, 0, 0, 0, 0.8f);
                font.drawText(cs, tx, ty, 1.5f, sw, sh, 1, 1, 1, 1);
            }
        }
    }

    private void renderChestSlots() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = chestX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy = chestY0 + (2 - row) * (SLOT_SIZE + SLOT_GAP);
                fillRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 0.22f, 0.18f, 0.14f, 0.8f);

                int chestSlot = row * 9 + col;
                Inventory.ItemStack stack = currentChest.getSlot(chestSlot);
                if (stack != null && !stack.isEmpty()) {
                    renderItemPreview(sx, sy, stack);
                }
                strokeRect(sx, sy, SLOT_SIZE, SLOT_SIZE, BORDER, 0.5f, 0.4f, 0.25f, 0.8f);
            }
        }
    }

    private void renderPlayerSlots(Inventory inventory) {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = invX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy;
                if (row == 0) sy = invY0;
                else sy = invY0 + SLOT_SIZE + ROW_GAP + (row - 1) * (SLOT_SIZE + SLOT_GAP);
                int slot = (row == 0) ? col : (9 + (row - 1) * 9 + col);

                fillRect(sx, sy, SLOT_SIZE, SLOT_SIZE, 0.2f, 0.2f, 0.2f, 0.8f);
                Inventory.ItemStack stack = inventory.getSlot(slot);
                if (stack != null && !stack.isEmpty()) {
                    renderItemPreview(sx, sy, stack);
                }
                strokeRect(sx, sy, SLOT_SIZE, SLOT_SIZE, BORDER, 0.4f, 0.4f, 0.4f, 0.7f);
            }
        }
    }

    private void renderItemPreview(float sx, float sy, Inventory.ItemStack stack) {
        int bid = stack.getBlockId();
        if (bid > 0 && bid < BLOCK_COLORS.length) {
            float[] c = BLOCK_COLORS[bid];
            float off = (SLOT_SIZE - PREVIEW_SIZE) / 2f;
            fillRect(sx + off, sy + off, PREVIEW_SIZE, PREVIEW_SIZE, c[0], c[1], c[2], c[3]);
        }
    }

    private void renderSlotCounts(Inventory playerInventory) {
        // Chest slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = chestX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy = chestY0 + (2 - row) * (SLOT_SIZE + SLOT_GAP);
                int chestSlot = row * 9 + col;
                Inventory.ItemStack stack = currentChest.getSlot(chestSlot);
                if (stack != null && stack.getCount() > 1 && !stack.hasDurability()) {
                    renderCountText(sx, sy, stack.getCount());
                }
            }
        }
        // Player slots
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = invX0 + col * (SLOT_SIZE + SLOT_GAP);
                float sy;
                if (row == 0) sy = invY0;
                else sy = invY0 + SLOT_SIZE + ROW_GAP + (row - 1) * (SLOT_SIZE + SLOT_GAP);
                int slot = (row == 0) ? col : (9 + (row - 1) * 9 + col);
                Inventory.ItemStack stack = playerInventory.getSlot(slot);
                if (stack != null && stack.getCount() > 1 && !stack.hasDurability()) {
                    renderCountText(sx, sy, stack.getCount());
                }
            }
        }
    }

    private void renderCountText(float sx, float sy, int count) {
        String cs = String.valueOf(count);
        float tx = sx + SLOT_SIZE - 8 * cs.length() - 2;
        float ty = sy + 2;
        font.drawText(cs, tx + 1, ty + 1, 1.5f, sw, sh, 0, 0, 0, 0.8f);
        font.drawText(cs, tx, ty, 1.5f, sw, sh, 1, 1, 1, 1);
    }

    // ---- Drawing helpers ----

    private void fillRect(float x, float y, float w, float h,
                          float r, float g, float b, float a) {
        setProjection(new Matrix4f().ortho(
            -x / w, (sw - x) / w,
            -y / h, (sh - y) / h,
            -1, 1));
        uiShader.setVec4("uColor", r, g, b, a);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    private void strokeRect(float x, float y, float w, float h, float bw,
                             float r, float g, float b, float a) {
        fillRect(x, y + h - bw, w, bw, r, g, b, a);
        fillRect(x, y, w, bw, r, g, b, a);
        fillRect(x, y, bw, h, r, g, b, a);
        fillRect(x + w - bw, y, bw, h, r, g, b, a);
    }

    private void setProjection(Matrix4f proj) {
        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(16);
            proj.get(fb);
            glUniformMatrix4fv(
                glGetUniformLocation(uiShader.getProgramId(), "uProjection"),
                false, fb);
        }
    }

    public void cleanup() {
        if (quadVbo != 0) glDeleteBuffers(quadVbo);
        if (quadVao != 0) glDeleteVertexArrays(quadVao);
        if (uiShader != null) uiShader.cleanup();
    }
}
