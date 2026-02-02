package com.voxelgame.ui;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/**
 * Settings screen with sliders for render distance, FOV, mouse sensitivity,
 * LOD threshold, and max LOD render distance.
 * Accessible from main menu or pause menu.
 */
public class SettingsScreen extends Screen {

    /** Callback when settings screen is closed. */
    public interface SettingsCallback {
        void onBack();
    }

    /** Listener for live settings changes. */
    public interface SettingsChangeListener {
        void onRenderDistanceChanged(int chunks);
        void onFovChanged(float fov);
        void onMouseSensitivityChanged(float sensitivity);
        void onLodThresholdChanged(int chunks);
        void onMaxLodDistanceChanged(int chunks);
    }

    private SettingsCallback callback;
    private SettingsChangeListener changeListener;

    // Current settings values
    private int renderDistance = 8;         // chunks (4-16) — LOD 0 radius
    private float fov = 70.0f;             // degrees (50-120)
    private float mouseSensitivity = 0.1f; // (0.01-0.5)
    private int lodThreshold = 8;          // where LOD starts (4-16)
    private int maxLodDistance = 20;        // max render distance (16-40)

    // Slider config
    private static final int MIN_RENDER_DIST = 4;
    private static final int MAX_RENDER_DIST = 16;
    private static final float MIN_FOV = 50.0f;
    private static final float MAX_FOV = 120.0f;
    private static final float MIN_SENS = 0.01f;
    private static final float MAX_SENS = 0.50f;
    private static final int MIN_LOD_THRESHOLD = 4;
    private static final int MAX_LOD_THRESHOLD = 16;
    private static final int MIN_LOD_DIST = 12;
    private static final int MAX_LOD_DIST = 40;

    private static final float SLIDER_WIDTH = 360.0f;
    private static final float SLIDER_HEIGHT = 8.0f;
    private static final float SLIDER_KNOB_SIZE = 16.0f;

    // Drag tracking
    private int draggingSlider = -1; // 0=RD, 1=FOV, 2=Sens, 3=LOD Threshold, 4=Max LOD Dist
    private int hoveredButton = -1;  // 0-4 = sliders, 5 = Done

    private static final int SLIDER_COUNT = 5;

    public void setCallback(SettingsCallback callback) {
        this.callback = callback;
    }

    public void setChangeListener(SettingsChangeListener listener) {
        this.changeListener = listener;
    }

    // ---- Getters / Setters for current values ----

    public int getRenderDistance() { return renderDistance; }
    public void setRenderDistance(int rd) { this.renderDistance = Math.max(MIN_RENDER_DIST, Math.min(MAX_RENDER_DIST, rd)); }

    public float getFov() { return fov; }
    public void setFov(float fov) { this.fov = Math.max(MIN_FOV, Math.min(MAX_FOV, fov)); }

    public float getMouseSensitivity() { return mouseSensitivity; }
    public void setMouseSensitivity(float s) { this.mouseSensitivity = Math.max(MIN_SENS, Math.min(MAX_SENS, s)); }

    public int getLodThreshold() { return lodThreshold; }
    public void setLodThreshold(int t) { this.lodThreshold = Math.max(MIN_LOD_THRESHOLD, Math.min(MAX_LOD_THRESHOLD, t)); }

    public int getMaxLodDistance() { return maxLodDistance; }
    public void setMaxLodDistance(int d) { this.maxLodDistance = Math.max(MIN_LOD_DIST, Math.min(MAX_LOD_DIST, d)); }

    @Override
    public void render(int screenW, int screenH, float dt) {
        this.screenW = screenW;
        this.screenH = screenH;

        beginDraw(screenW, screenH);

        // Background
        fillRect(0, 0, screenW, screenH, 0.08f, 0.09f, 0.12f, 1.0f);

        endDraw();

        // Title
        drawCenteredTextWithShadow("Settings", screenH * 0.05f, 3.5f,
                                    0.8f, 0.9f, 1.0f, 1.0f);

        // Layout
        float formX = (screenW - SLIDER_WIDTH) / 2.0f;
        float formY = screenH * 0.15f;
        float rowH = 65.0f;

        // ---- Render Distance Slider (LOD 0 radius) ----
        renderSlider("Full Detail Distance: " + renderDistance + " chunks",
                     formX, formY, SLIDER_WIDTH,
                     (renderDistance - MIN_RENDER_DIST) / (float)(MAX_RENDER_DIST - MIN_RENDER_DIST),
                     hoveredButton == 0 || draggingSlider == 0);
        formY += rowH;

        // ---- LOD Threshold Slider ----
        renderSlider("LOD Threshold: " + lodThreshold + " chunks",
                     formX, formY, SLIDER_WIDTH,
                     (lodThreshold - MIN_LOD_THRESHOLD) / (float)(MAX_LOD_THRESHOLD - MIN_LOD_THRESHOLD),
                     hoveredButton == 1 || draggingSlider == 1);
        formY += rowH;

        // ---- Max LOD Distance Slider ----
        renderSlider("Max Render Distance: " + maxLodDistance + " chunks",
                     formX, formY, SLIDER_WIDTH,
                     (maxLodDistance - MIN_LOD_DIST) / (float)(MAX_LOD_DIST - MIN_LOD_DIST),
                     hoveredButton == 2 || draggingSlider == 2);
        formY += rowH;

        // ---- FOV Slider ----
        renderSlider("FOV: " + (int) fov + "\u00B0",
                     formX, formY, SLIDER_WIDTH,
                     (fov - MIN_FOV) / (MAX_FOV - MIN_FOV),
                     hoveredButton == 3 || draggingSlider == 3);
        formY += rowH;

        // ---- Mouse Sensitivity Slider ----
        String sensStr = String.format("Mouse Sensitivity: %.2f", mouseSensitivity);
        renderSlider(sensStr,
                     formX, formY, SLIDER_WIDTH,
                     (mouseSensitivity - MIN_SENS) / (MAX_SENS - MIN_SENS),
                     hoveredButton == 4 || draggingSlider == 4);
        formY += rowH;

        // ---- Done button ----
        beginDraw(screenW, screenH);
        float btnX = (screenW - BUTTON_WIDTH) / 2.0f;
        float btnY = screenH - (screenH * 0.90f) - BUTTON_HEIGHT;
        drawButton("Done", btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT, hoveredButton == SLIDER_COUNT, true);
        endDraw();
    }

    /**
     * Render a labeled slider.
     */
    private void renderSlider(String label, float x, float topY, float w,
                               float value, boolean highlighted) {
        // Label
        font.drawText(label, x, topY, 2.0f, screenW, screenH,
                       0.8f, 0.9f, 1.0f, 0.9f);

        // Slider track
        float trackTopY = topY + 28;
        float trackY = screenH - trackTopY - SLIDER_HEIGHT;

        beginDraw(screenW, screenH);

        // Track background
        fillRect(x, trackY, w, SLIDER_HEIGHT, 0.15f, 0.15f, 0.2f, 0.8f);

        // Track fill
        float fillW = w * Math.max(0, Math.min(1, value));
        fillRect(x, trackY, fillW, SLIDER_HEIGHT, 0.3f, 0.5f, 0.8f, 0.9f);

        // Knob
        float knobX = x + fillW - SLIDER_KNOB_SIZE / 2.0f;
        float knobY = trackY - (SLIDER_KNOB_SIZE - SLIDER_HEIGHT) / 2.0f;
        if (highlighted) {
            fillRect(knobX, knobY, SLIDER_KNOB_SIZE, SLIDER_KNOB_SIZE,
                     0.5f, 0.7f, 1.0f, 1.0f);
        } else {
            fillRect(knobX, knobY, SLIDER_KNOB_SIZE, SLIDER_KNOB_SIZE,
                     0.8f, 0.8f, 0.9f, 1.0f);
        }
        strokeRect(knobX, knobY, SLIDER_KNOB_SIZE, SLIDER_KNOB_SIZE, 1,
                   0.4f, 0.4f, 0.5f, 0.8f);

        endDraw();
    }

    // ---- Slider Y positions (centralized for hit testing) ----

    private float sliderTrackY(int index) {
        float formY = screenH * 0.15f + index * 65.0f + 28;
        return screenH - formY - SLIDER_HEIGHT;
    }

    private float sliderTrackX() {
        return (screenW - SLIDER_WIDTH) / 2.0f;
    }

    @Override
    public void handleClick(double mx, double my, int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;

        float clickY = screenH - (float) my;
        float clickX = (float) mx;

        float sx = sliderTrackX();

        // Check slider clicks (start drag)
        for (int i = 0; i < SLIDER_COUNT; i++) {
            float trackY = sliderTrackY(i);
            float hitY = trackY - SLIDER_KNOB_SIZE;
            float hitH = SLIDER_HEIGHT + SLIDER_KNOB_SIZE * 2;
            if (isInside(clickX, clickY, sx, hitY, SLIDER_WIDTH, hitH)) {
                draggingSlider = i;
                updateSliderFromMouse(clickX);
                return;
            }
        }

        // Done button
        float btnX = (screenW - BUTTON_WIDTH) / 2.0f;
        float btnY = screenH - (screenH * 0.90f) - BUTTON_HEIGHT;
        if (isInside(clickX, clickY, btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            if (callback != null) callback.onBack();
        }

        draggingSlider = -1;
    }

    /**
     * Handle mouse drag (call from GameLoop on mouse move while button held).
     */
    public void handleDrag(double mx, double my, int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
        if (draggingSlider >= 0) {
            updateSliderFromMouse((float) mx);
        }
    }

    /**
     * Release drag on mouse button up.
     */
    public void handleRelease() {
        draggingSlider = -1;
    }

    private void updateSliderFromMouse(float mouseX) {
        float sx = sliderTrackX();
        float t = (mouseX - sx) / SLIDER_WIDTH;
        t = Math.max(0, Math.min(1, t));

        switch (draggingSlider) {
            case 0 -> {
                renderDistance = Math.round(MIN_RENDER_DIST + t * (MAX_RENDER_DIST - MIN_RENDER_DIST));
                if (changeListener != null) changeListener.onRenderDistanceChanged(renderDistance);
            }
            case 1 -> {
                lodThreshold = Math.round(MIN_LOD_THRESHOLD + t * (MAX_LOD_THRESHOLD - MIN_LOD_THRESHOLD));
                if (changeListener != null) changeListener.onLodThresholdChanged(lodThreshold);
            }
            case 2 -> {
                maxLodDistance = Math.round(MIN_LOD_DIST + t * (MAX_LOD_DIST - MIN_LOD_DIST));
                if (changeListener != null) changeListener.onMaxLodDistanceChanged(maxLodDistance);
            }
            case 3 -> {
                fov = MIN_FOV + t * (MAX_FOV - MIN_FOV);
                if (changeListener != null) changeListener.onFovChanged(fov);
            }
            case 4 -> {
                mouseSensitivity = MIN_SENS + t * (MAX_SENS - MIN_SENS);
                if (changeListener != null) changeListener.onMouseSensitivityChanged(mouseSensitivity);
            }
        }
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

        float sx = sliderTrackX();

        // Sliders
        for (int i = 0; i < SLIDER_COUNT; i++) {
            float trackY = sliderTrackY(i);
            float hitY = trackY - SLIDER_KNOB_SIZE;
            float hitH = SLIDER_HEIGHT + SLIDER_KNOB_SIZE * 2;
            if (isInside(clickX, clickY, sx, hitY, SLIDER_WIDTH, hitH)) {
                hoveredButton = i;
                return;
            }
        }

        // Done button
        float btnX = (screenW - BUTTON_WIDTH) / 2.0f;
        float btnY = screenH - (screenH * 0.90f) - BUTTON_HEIGHT;
        if (isInside(clickX, clickY, btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            hoveredButton = SLIDER_COUNT;
        }
    }

    @Override
    public boolean handleKeyPress(int key) {
        if (key == GLFW_KEY_ESCAPE) {
            if (callback != null) callback.onBack();
            return true;
        }
        return false;
    }
}
