package com.voxelgame.ui;

import static org.lwjgl.opengl.GL33.*;

/**
 * Pause menu screen shown when ESC is pressed in-game.
 * Options: Resume, Settings, Save & Quit to Title, Quit Game.
 */
public class PauseMenuScreen extends Screen {

    /** Callback interface for pause menu actions. */
    public interface PauseCallback {
        void onResume();
        void onSettings();
        void onSaveAndQuit();
        void onQuitGame();
    }

    private PauseCallback callback;
    private int hoveredButton = -1; // 0=Resume, 1=Settings, 2=Save & Quit, 3=Quit Game

    public void setCallback(PauseCallback callback) {
        this.callback = callback;
    }

    @Override
    public void render(int screenW, int screenH, float dt) {
        this.screenW = screenW;
        this.screenH = screenH;

        beginDraw(screenW, screenH);

        // Semi-transparent dark overlay
        fillRect(0, 0, screenW, screenH, 0.0f, 0.0f, 0.0f, 0.6f);

        endDraw();

        // Title
        drawCenteredTextWithShadow("Game Menu", screenH * 0.18f, 4.0f,
                                    0.8f, 0.9f, 1.0f, 1.0f);

        // Buttons
        beginDraw(screenW, screenH);

        float btnX = (screenW - BUTTON_WIDTH) / 2.0f;
        float startY = screenH * 0.35f;
        float btn0Y = screenH - startY - BUTTON_HEIGHT;
        float btn1Y = btn0Y - BUTTON_HEIGHT - BUTTON_GAP;
        float btn2Y = btn1Y - BUTTON_HEIGHT - BUTTON_GAP * 3; // extra gap
        float btn3Y = btn2Y - BUTTON_HEIGHT - BUTTON_GAP;

        drawButton("Back to Game", btnX, btn0Y, BUTTON_WIDTH, BUTTON_HEIGHT, hoveredButton == 0, true);
        drawButton("Settings", btnX, btn1Y, BUTTON_WIDTH, BUTTON_HEIGHT, hoveredButton == 1, true);
        drawButton("Save and Quit to Title", btnX, btn2Y, BUTTON_WIDTH, BUTTON_HEIGHT, hoveredButton == 2, true);
        drawButton("Quit Game", btnX, btn3Y, BUTTON_WIDTH, BUTTON_HEIGHT, hoveredButton == 3, true);

        endDraw();
    }

    @Override
    public void handleClick(double mx, double my, int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;

        float clickY = screenH - (float) my;
        float clickX = (float) mx;

        float btnX = (screenW - BUTTON_WIDTH) / 2.0f;
        float startY = screenH * 0.35f;
        float btn0Y = screenH - startY - BUTTON_HEIGHT;
        float btn1Y = btn0Y - BUTTON_HEIGHT - BUTTON_GAP;
        float btn2Y = btn1Y - BUTTON_HEIGHT - BUTTON_GAP * 3;
        float btn3Y = btn2Y - BUTTON_HEIGHT - BUTTON_GAP;

        if (isInside(clickX, clickY, btnX, btn0Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            if (callback != null) callback.onResume();
        } else if (isInside(clickX, clickY, btnX, btn1Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            if (callback != null) callback.onSettings();
        } else if (isInside(clickX, clickY, btnX, btn2Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            if (callback != null) callback.onSaveAndQuit();
        } else if (isInside(clickX, clickY, btnX, btn3Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            if (callback != null) callback.onQuitGame();
        }
    }

    /**
     * Update hover state from mouse position.
     */
    public void updateHover(double mx, double my, int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
        hoveredButton = -1;

        float clickY = screenH - (float) my;
        float clickX = (float) mx;

        float btnX = (screenW - BUTTON_WIDTH) / 2.0f;
        float startY = screenH * 0.35f;
        float btn0Y = screenH - startY - BUTTON_HEIGHT;
        float btn1Y = btn0Y - BUTTON_HEIGHT - BUTTON_GAP;
        float btn2Y = btn1Y - BUTTON_HEIGHT - BUTTON_GAP * 3;
        float btn3Y = btn2Y - BUTTON_HEIGHT - BUTTON_GAP;

        if (isInside(clickX, clickY, btnX, btn0Y, BUTTON_WIDTH, BUTTON_HEIGHT)) hoveredButton = 0;
        else if (isInside(clickX, clickY, btnX, btn1Y, BUTTON_WIDTH, BUTTON_HEIGHT)) hoveredButton = 1;
        else if (isInside(clickX, clickY, btnX, btn2Y, BUTTON_WIDTH, BUTTON_HEIGHT)) hoveredButton = 2;
        else if (isInside(clickX, clickY, btnX, btn3Y, BUTTON_WIDTH, BUTTON_HEIGHT)) hoveredButton = 3;
    }
}
