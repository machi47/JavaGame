package com.voxelgame.ui;

import com.voxelgame.sim.Difficulty;
import com.voxelgame.sim.GameMode;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/**
 * World creation screen. Configure name, game mode, difficulty, seed,
 * and advanced options before creating a new world.
 */
public class WorldCreationScreen extends Screen {

    /** Callback when world creation is confirmed or cancelled. */
    public interface CreationCallback {
        void onCreateWorld(String worldName, GameMode gameMode, Difficulty difficulty,
                           String seed, boolean showCoordinates, boolean bonusChest);
        void onCancel();
    }

    private CreationCallback callback;

    // Form state
    private String worldName = "New World";
    private int gameModeIndex = 1; // 0=Creative, 1=Survival
    private int difficultyIndex = 2; // 0=Peaceful, 1=Easy, 2=Normal, 3=Hard
    private String seedText = "";
    private boolean showCoordinates = true;
    private boolean bonusChest = false;
    private boolean showAdvanced = false;

    // Input focus: 0=world name, 1=seed
    private int focusedField = -1;

    // Cursor blink
    private float cursorTimer = 0;
    private boolean cursorVisible = true;

    // Hover tracking
    private int hoveredButton = -1;
    // 0=Game Mode, 1=Difficulty, 2=Seed field, 3=Name field
    // 4=More Options, 5=Show Coords, 6=Bonus Chest
    // 7=Create, 8=Cancel

    private static final GameMode[] GAME_MODES = { GameMode.CREATIVE, GameMode.SURVIVAL };
    private static final Difficulty[] DIFFICULTIES = { Difficulty.PEACEFUL, Difficulty.EASY,
                                                        Difficulty.NORMAL, Difficulty.HARD };
    private static final String[] MODE_NAMES = { "Creative", "Survival" };
    private static final String[] DIFF_NAMES = { "Peaceful", "Easy", "Normal", "Hard" };

    public void setCallback(CreationCallback callback) {
        this.callback = callback;
    }

    /** Reset form to defaults (call when opening screen). */
    public void reset() {
        worldName = "New World";
        gameModeIndex = 1;
        difficultyIndex = 2;
        seedText = "";
        showCoordinates = true;
        bonusChest = false;
        showAdvanced = false;
        focusedField = -1;
    }

    @Override
    public void render(int screenW, int screenH, float dt) {
        this.screenW = screenW;
        this.screenH = screenH;

        cursorTimer += dt;
        if (cursorTimer > 0.5f) {
            cursorTimer = 0;
            cursorVisible = !cursorVisible;
        }

        beginDraw(screenW, screenH);

        // Background
        fillRect(0, 0, screenW, screenH, 0.08f, 0.09f, 0.12f, 1.0f);

        endDraw();

        // Title
        drawCenteredTextWithShadow("Create New World", screenH * 0.05f, 3.0f,
                                    0.8f, 0.9f, 1.0f, 1.0f);

        // Form layout
        float formX = (screenW - 400) / 2.0f;
        float formY = screenH * 0.14f; // top-left origin
        float rowH = 44.0f;
        float labelScale = 2.0f;
        float fieldW = 400.0f;

        // ---- World Name ----
        renderLabel("World Name:", formX, formY, labelScale);
        formY += 20;
        renderTextField(worldName, formX, formY, fieldW, 32, focusedField == 0);
        formY += rowH;

        // ---- Game Mode ----
        renderLabel("Game Mode:", formX, formY, labelScale);
        formY += 20;
        beginDraw(screenW, screenH);
        float modeY = screenH - formY - 32;
        drawButton("< " + MODE_NAMES[gameModeIndex] + " >", formX, modeY, fieldW, 32,
                   hoveredButton == 0, true);
        endDraw();
        formY += rowH;

        // ---- Difficulty ----
        renderLabel("Difficulty:", formX, formY, labelScale);
        formY += 20;
        beginDraw(screenW, screenH);
        float diffY = screenH - formY - 32;
        drawButton("< " + DIFF_NAMES[difficultyIndex] + " >", formX, diffY, fieldW, 32,
                   hoveredButton == 1, true);
        endDraw();
        formY += rowH;

        // ---- Seed ----
        renderLabel("Seed (leave empty for random):", formX, formY, labelScale);
        formY += 20;
        renderTextField(seedText, formX, formY, fieldW, 32, focusedField == 1);
        formY += rowH;

        // ---- More World Options ----
        beginDraw(screenW, screenH);
        float moreY = screenH - formY - 32;
        String moreLabel = showAdvanced ? "v Hide Advanced Options" : "> More World Options...";
        drawButton(moreLabel, formX, moreY, fieldW, 32, hoveredButton == 4, true);
        endDraw();
        formY += rowH;

        // ---- Advanced options (if shown) ----
        if (showAdvanced) {
            renderToggle("Show Coordinates", showCoordinates, formX, formY, fieldW, hoveredButton == 5);
            formY += 36;
            renderToggle("Bonus Chest", bonusChest, formX, formY, fieldW, hoveredButton == 6);
            formY += rowH;
        }

        // ---- Create / Cancel buttons ----
        float btnRowY = screenH * 0.88f;
        float halfW = (fieldW - BUTTON_GAP) / 2.0f;

        beginDraw(screenW, screenH);
        float createY = screenH - btnRowY - BUTTON_HEIGHT;
        boolean canCreate = !worldName.trim().isEmpty();
        drawButton("Create World", formX, createY, halfW, BUTTON_HEIGHT,
                   hoveredButton == 7, canCreate);
        drawButton("Cancel", formX + halfW + BUTTON_GAP, createY, halfW, BUTTON_HEIGHT,
                   hoveredButton == 8, true);
        endDraw();
    }

    private void renderLabel(String text, float x, float topY, float scale) {
        font.drawText(text, x, topY, scale, screenW, screenH,
                       0.7f, 0.8f, 0.9f, 0.9f);
    }

    private void renderTextField(String text, float formX, float topY, float fieldW,
                                  float fieldH, boolean focused) {
        beginDraw(screenW, screenH);
        float fieldY = screenH - topY - fieldH;

        // Background
        fillRect(formX, fieldY, fieldW, fieldH, 0.05f, 0.05f, 0.08f, 0.9f);

        // Border (highlighted if focused)
        if (focused) {
            strokeRect(formX, fieldY, fieldW, fieldH, 2, 0.4f, 0.6f, 0.9f, 1.0f);
        } else {
            strokeRect(formX, fieldY, fieldW, fieldH, 1, 0.3f, 0.3f, 0.3f, 0.7f);
        }

        endDraw();

        // Text content
        String displayText = text;
        if (focused && cursorVisible) {
            displayText += "_";
        }
        font.drawText(displayText, formX + 6, topY + 8, 2.0f, screenW, screenH,
                       1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderToggle(String label, boolean value, float formX, float topY,
                                float fieldW, boolean hovered) {
        beginDraw(screenW, screenH);
        float toggleY = screenH - topY - 28;

        // Toggle box
        float boxSize = 24;
        float boxX = formX;
        if (hovered) {
            fillRect(boxX, toggleY + 2, boxSize, boxSize, 0.2f, 0.3f, 0.4f, 0.8f);
        } else {
            fillRect(boxX, toggleY + 2, boxSize, boxSize, 0.1f, 0.1f, 0.13f, 0.8f);
        }
        strokeRect(boxX, toggleY + 2, boxSize, boxSize, 2, 0.4f, 0.4f, 0.4f, 0.8f);

        // Checkmark
        if (value) {
            fillRect(boxX + 5, toggleY + 7, boxSize - 10, boxSize - 10,
                     0.3f, 0.8f, 0.3f, 1.0f);
        }

        endDraw();

        // Label
        font.drawText(label, formX + boxSize + 10, topY + 4, 2.0f,
                       screenW, screenH, 0.8f, 0.8f, 0.8f, 0.9f);
    }

    @Override
    public void handleClick(double mx, double my, int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;

        float clickY = screenH - (float) my;
        float clickX = (float) mx;

        float formX = (screenW - 400) / 2.0f;
        float formTopY = screenH * 0.14f;
        float rowH = 44.0f;
        float fieldW = 400.0f;

        float y = formTopY;

        // World name field
        y += 20;
        float nameFieldY = screenH - y - 32;
        if (isInside(clickX, clickY, formX, nameFieldY, fieldW, 32)) {
            focusedField = 0;
            return;
        }
        y += rowH;

        // Game mode button
        y += 20;
        float modeY = screenH - y - 32;
        if (isInside(clickX, clickY, formX, modeY, fieldW, 32)) {
            gameModeIndex = (gameModeIndex + 1) % GAME_MODES.length;
            return;
        }
        y += rowH;

        // Difficulty button
        y += 20;
        float diffY = screenH - y - 32;
        if (isInside(clickX, clickY, formX, diffY, fieldW, 32)) {
            difficultyIndex = (difficultyIndex + 1) % DIFFICULTIES.length;
            return;
        }
        y += rowH;

        // Seed field
        y += 20;
        float seedFieldY = screenH - y - 32;
        if (isInside(clickX, clickY, formX, seedFieldY, fieldW, 32)) {
            focusedField = 1;
            return;
        }
        y += rowH;

        // More options button
        float moreY = screenH - y - 32;
        if (isInside(clickX, clickY, formX, moreY, fieldW, 32)) {
            showAdvanced = !showAdvanced;
            return;
        }
        y += rowH;

        // Advanced options toggles
        if (showAdvanced) {
            // Show coordinates
            float coordY = screenH - y - 28;
            if (isInside(clickX, clickY, formX, coordY, fieldW, 28)) {
                showCoordinates = !showCoordinates;
                return;
            }
            y += 36;

            // Bonus chest
            float bonusY = screenH - y - 28;
            if (isInside(clickX, clickY, formX, bonusY, fieldW, 28)) {
                bonusChest = !bonusChest;
                return;
            }
            y += rowH;
        }

        // Create / Cancel buttons
        float btnRowY = screenH * 0.88f;
        float halfW = (fieldW - BUTTON_GAP) / 2.0f;
        float createY = screenH - btnRowY - BUTTON_HEIGHT;

        if (isInside(clickX, clickY, formX, createY, halfW, BUTTON_HEIGHT)) {
            if (!worldName.trim().isEmpty() && callback != null) {
                callback.onCreateWorld(worldName.trim(), GAME_MODES[gameModeIndex],
                    DIFFICULTIES[difficultyIndex], seedText.trim(),
                    showCoordinates, bonusChest);
            }
        } else if (isInside(clickX, clickY, formX + halfW + BUTTON_GAP, createY, halfW, BUTTON_HEIGHT)) {
            if (callback != null) callback.onCancel();
        }

        // Click outside fields = defocus
        focusedField = -1;
    }

    @Override
    public boolean handleKeyPress(int key) {
        if (focusedField < 0) return false;

        if (key == GLFW_KEY_BACKSPACE) {
            if (focusedField == 0 && !worldName.isEmpty()) {
                worldName = worldName.substring(0, worldName.length() - 1);
            } else if (focusedField == 1 && !seedText.isEmpty()) {
                seedText = seedText.substring(0, seedText.length() - 1);
            }
            cursorTimer = 0;
            cursorVisible = true;
            return true;
        }

        if (key == GLFW_KEY_TAB) {
            focusedField = (focusedField + 1) % 2;
            cursorTimer = 0;
            cursorVisible = true;
            return true;
        }

        if (key == GLFW_KEY_ENTER) {
            if (!worldName.trim().isEmpty() && callback != null) {
                callback.onCreateWorld(worldName.trim(), GAME_MODES[gameModeIndex],
                    DIFFICULTIES[difficultyIndex], seedText.trim(),
                    showCoordinates, bonusChest);
            }
            return true;
        }

        if (key == GLFW_KEY_ESCAPE) {
            if (callback != null) callback.onCancel();
            return true;
        }

        return false;
    }

    @Override
    public void handleCharTyped(char c) {
        if (focusedField < 0) return;
        if (c < 32 || c > 126) return; // printable ASCII only

        if (focusedField == 0 && worldName.length() < 30) {
            worldName += c;
        } else if (focusedField == 1 && seedText.length() < 20) {
            seedText += c;
        }
        cursorTimer = 0;
        cursorVisible = true;
    }

    /**
     * Update hover state.
     */
    public void updateHover(double mx, double my, int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
        hoveredButton = -1;

        float clickY = screenH - (float) my;
        float clickX = (float) mx;

        float formX = (screenW - 400) / 2.0f;
        float formTopY = screenH * 0.14f;
        float rowH = 44.0f;
        float fieldW = 400.0f;

        float y = formTopY;

        // Name field
        y += 20;
        float nameFieldY = screenH - y - 32;
        if (isInside(clickX, clickY, formX, nameFieldY, fieldW, 32)) { hoveredButton = 3; return; }
        y += rowH;

        // Game mode
        y += 20;
        float modeY = screenH - y - 32;
        if (isInside(clickX, clickY, formX, modeY, fieldW, 32)) { hoveredButton = 0; return; }
        y += rowH;

        // Difficulty
        y += 20;
        float diffY = screenH - y - 32;
        if (isInside(clickX, clickY, formX, diffY, fieldW, 32)) { hoveredButton = 1; return; }
        y += rowH;

        // Seed
        y += 20;
        float seedFieldY = screenH - y - 32;
        if (isInside(clickX, clickY, formX, seedFieldY, fieldW, 32)) { hoveredButton = 2; return; }
        y += rowH;

        // More options
        float moreY = screenH - y - 32;
        if (isInside(clickX, clickY, formX, moreY, fieldW, 32)) { hoveredButton = 4; return; }
        y += rowH;

        if (showAdvanced) {
            float coordY = screenH - y - 28;
            if (isInside(clickX, clickY, formX, coordY, fieldW, 28)) { hoveredButton = 5; return; }
            y += 36;

            float bonusY = screenH - y - 28;
            if (isInside(clickX, clickY, formX, bonusY, fieldW, 28)) { hoveredButton = 6; return; }
            y += rowH;
        }

        // Buttons
        float btnRowY = screenH * 0.88f;
        float halfW = (fieldW - BUTTON_GAP) / 2.0f;
        float createY = screenH - btnRowY - BUTTON_HEIGHT;

        if (isInside(clickX, clickY, formX, createY, halfW, BUTTON_HEIGHT)) hoveredButton = 7;
        else if (isInside(clickX, clickY, formX + halfW + BUTTON_GAP, createY, halfW, BUTTON_HEIGHT)) hoveredButton = 8;
    }

    public boolean hasFocusedField() { return focusedField >= 0; }
}
