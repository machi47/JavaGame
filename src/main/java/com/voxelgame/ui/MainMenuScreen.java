package com.voxelgame.ui;

import static org.lwjgl.opengl.GL33.*;

/**
 * Main menu title screen.
 * Shows "VoxelGame" title with Singleplayer, Settings, and Quit buttons.
 */
public class MainMenuScreen extends Screen {

    /** Callback interface for menu actions. */
    public interface MenuCallback {
        void onSingleplayer();
        void onSettings();
        void onQuit();
    }

    private MenuCallback callback;
    private float animTimer = 0;

    // Hover tracking
    private int hoveredButton = -1; // 0=Singleplayer, 1=Settings, 2=Quit

    public void setCallback(MenuCallback callback) {
        this.callback = callback;
    }

    @Override
    public void render(int screenW, int screenH, float dt) {
        this.screenW = screenW;
        this.screenH = screenH;
        animTimer += dt;

        // ---- Full-screen background ----
        beginDraw(screenW, screenH);

        // Dark blue-gray gradient background
        fillRect(0, 0, screenW, screenH, 0.08f, 0.09f, 0.12f, 1.0f);

        // Subtle animated vignette overlay
        float vigAlpha = 0.3f + 0.05f * (float) Math.sin(animTimer * 0.5);
        fillRect(0, 0, screenW, screenH * 0.3f, 0.0f, 0.0f, 0.0f, vigAlpha);
        fillRect(0, screenH * 0.7f, screenW, screenH * 0.3f, 0.0f, 0.0f, 0.0f, vigAlpha * 0.5f);

        // Decorative lines
        float lineY = screenH * 0.38f;
        fillRect(screenW * 0.15f, lineY, screenW * 0.7f, 2, 0.3f, 0.4f, 0.5f, 0.4f);
        fillRect(screenW * 0.15f, lineY - screenH * 0.35f, screenW * 0.7f, 2, 0.3f, 0.4f, 0.5f, 0.2f);

        endDraw();

        // ---- Title ----
        float titleScale = 5.0f;
        String title = "VoxelGame";
        float titleY = screenH * 0.15f;

        // Shadow
        drawCenteredTextWithShadow(title, titleY, titleScale, 0.6f, 0.8f, 1.0f, 1.0f);

        // Subtitle
        String subtitle = "A voxel world engine";
        float subY = titleY + titleScale * CHAR_W + 12;
        drawCenteredText(subtitle, subY, 2.0f, 0.5f, 0.5f, 0.6f, 0.7f);

        // ---- Buttons ----
        beginDraw(screenW, screenH);

        float btnX = (screenW - BUTTON_WIDTH) / 2.0f;
        float startY = screenH * 0.45f;
        // Convert top-left Y to bottom-left Y for rendering
        float btn0Y = screenH - startY - BUTTON_HEIGHT;
        float btn1Y = btn0Y - BUTTON_HEIGHT - BUTTON_GAP;
        float btn2Y = btn1Y - BUTTON_HEIGHT - BUTTON_GAP * 3; // extra gap before Quit

        drawButton("Singleplayer", btnX, btn0Y, BUTTON_WIDTH, BUTTON_HEIGHT, hoveredButton == 0, true);
        drawButton("Settings", btnX, btn1Y, BUTTON_WIDTH, BUTTON_HEIGHT, hoveredButton == 1, true);
        drawButton("Quit", btnX, btn2Y, BUTTON_WIDTH, BUTTON_HEIGHT, hoveredButton == 2, true);

        endDraw();

        // ---- Version string (bottom left) ----
        font.drawText("v0.4.0", 8, screenH - 20, 1.5f, screenW, screenH,
                       0.4f, 0.4f, 0.4f, 0.6f);

        // ---- Copyright (bottom right) ----
        String copy = "Built by Machi + Patrick";
        float copyW = copy.length() * CHAR_W * 1.5f;
        font.drawText(copy, screenW - copyW - 8, screenH - 20, 1.5f, screenW, screenH,
                       0.4f, 0.4f, 0.4f, 0.6f);
    }

    @Override
    public void handleClick(double mx, double my, int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;

        // Convert from GLFW top-left origin to bottom-left origin
        float clickY = screenH - (float) my;
        float clickX = (float) mx;

        float btnX = (screenW - BUTTON_WIDTH) / 2.0f;
        float startY = screenH * 0.45f;
        float btn0Y = screenH - startY - BUTTON_HEIGHT;
        float btn1Y = btn0Y - BUTTON_HEIGHT - BUTTON_GAP;
        float btn2Y = btn1Y - BUTTON_HEIGHT - BUTTON_GAP * 3;

        if (isInside(clickX, clickY, btnX, btn0Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            if (callback != null) callback.onSingleplayer();
        } else if (isInside(clickX, clickY, btnX, btn1Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            if (callback != null) callback.onSettings();
        } else if (isInside(clickX, clickY, btnX, btn2Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            if (callback != null) callback.onQuit();
        }
    }

    /**
     * Update hover state from mouse position (GLFW top-left origin).
     */
    public void updateHover(double mx, double my, int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
        hoveredButton = -1;

        float clickY = screenH - (float) my;
        float clickX = (float) mx;

        float btnX = (screenW - BUTTON_WIDTH) / 2.0f;
        float startY = screenH * 0.45f;
        float btn0Y = screenH - startY - BUTTON_HEIGHT;
        float btn1Y = btn0Y - BUTTON_HEIGHT - BUTTON_GAP;
        float btn2Y = btn1Y - BUTTON_HEIGHT - BUTTON_GAP * 3;

        if (isInside(clickX, clickY, btnX, btn0Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            hoveredButton = 0;
        } else if (isInside(clickX, clickY, btnX, btn1Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            hoveredButton = 1;
        } else if (isInside(clickX, clickY, btnX, btn2Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            hoveredButton = 2;
        }
    }
}
