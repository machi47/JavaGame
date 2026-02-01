package com.voxelgame.ui;

import com.voxelgame.render.Shader;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Base class for all UI screens (main menu, world list, settings, etc.).
 * Provides shared quad rendering, fill/stroke helpers, and text drawing
 * via the shared BitmapFont and ui shader.
 */
public abstract class Screen {

    protected BitmapFont font;
    protected Shader uiShader;
    protected int quadVao, quadVbo;
    protected int screenW, screenH;

    // Button layout constants
    protected static final float BUTTON_WIDTH = 320.0f;
    protected static final float BUTTON_HEIGHT = 40.0f;
    protected static final float BUTTON_GAP = 8.0f;
    protected static final float CHAR_W = 8.0f;

    private boolean initialized = false;

    public void init(BitmapFont font) {
        this.font = font;
        uiShader = new Shader("shaders/ui.vert", "shaders/ui.frag");
        buildQuadVAO();
        initialized = true;
        onInit();
    }

    /** Override to add custom initialization after base init. */
    protected void onInit() {}

    private void buildQuadVAO() {
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

    // ---- Abstract methods ----

    /** Render the screen. Called each frame when this screen is active. */
    public abstract void render(int screenW, int screenH, float dt);

    /** Handle a mouse click at screen coordinates (top-left origin). */
    public abstract void handleClick(double mx, double my, int screenW, int screenH);

    /** Handle a key press. Return true if consumed. */
    public boolean handleKeyPress(int key) { return false; }

    /** Handle a character typed (for text input fields). */
    public void handleCharTyped(char c) {}

    // ---- Drawing helpers ----

    protected void beginDraw(int sw, int sh) {
        this.screenW = sw;
        this.screenH = sh;
        uiShader.bind();
        glBindVertexArray(quadVao);
    }

    protected void endDraw() {
        glBindVertexArray(0);
        uiShader.unbind();
    }

    protected void fillRect(float x, float y, float w, float h,
                             float r, float g, float b, float a) {
        setProjection(new Matrix4f().ortho(
            -x / w, (screenW - x) / w,
            -y / h, (screenH - y) / h,
            -1, 1));
        uiShader.setVec4("uColor", r, g, b, a);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    protected void strokeRect(float x, float y, float w, float h, float bw,
                               float r, float g, float b, float a) {
        fillRect(x, y + h - bw, w, bw, r, g, b, a);  // top
        fillRect(x, y, w, bw, r, g, b, a);            // bottom
        fillRect(x, y, bw, h, r, g, b, a);            // left
        fillRect(x + w - bw, y, bw, h, r, g, b, a);   // right
    }

    /** Draw a standard button. Returns true if the point (clickX, clickY) is inside. */
    protected boolean drawButton(String text, float x, float y, float w, float h,
                                  boolean hovered, boolean enabled) {
        // Background
        if (!enabled) {
            fillRect(x, y, w, h, 0.2f, 0.2f, 0.2f, 0.6f);
        } else if (hovered) {
            fillRect(x, y, w, h, 0.3f, 0.4f, 0.5f, 0.9f);
        } else {
            fillRect(x, y, w, h, 0.2f, 0.25f, 0.3f, 0.8f);
        }

        // Border
        float borderR = hovered ? 0.6f : 0.4f;
        float borderG = hovered ? 0.7f : 0.4f;
        float borderB = hovered ? 0.8f : 0.4f;
        strokeRect(x, y, w, h, 2, borderR, borderG, borderB, enabled ? 0.9f : 0.4f);

        // Text (centered)
        endDraw();
        float scale = 2.0f;
        float textW = text.length() * CHAR_W * scale;
        float textH = CHAR_W * scale;
        float textX = x + (w - textW) / 2.0f;
        float textY = y + (h - textH) / 2.0f;
        // Convert from bottom-left to top-left for drawText
        float topY = screenH - textY - textH;
        float textR = enabled ? 1.0f : 0.5f;
        float textG = enabled ? 1.0f : 0.5f;
        float textB = enabled ? 1.0f : 0.5f;
        font.drawText(text, textX, topY, scale, screenW, screenH, textR, textG, textB, 1.0f);
        beginDraw(screenW, screenH);

        return false; // doesn't handle click checking here
    }

    /** Check if a point (bottom-left origin) is inside a rectangle. */
    protected boolean isInside(float px, float py, float rx, float ry, float rw, float rh) {
        return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
    }

    /** Draw centered text at given Y position (top-left origin). */
    protected void drawCenteredText(String text, float y, float scale,
                                     float r, float g, float b, float a) {
        float textW = text.length() * CHAR_W * scale;
        float textX = (screenW - textW) / 2.0f;
        font.drawText(text, textX, y, scale, screenW, screenH, r, g, b, a);
    }

    /** Draw text with shadow. */
    protected void drawTextWithShadow(String text, float x, float y, float scale,
                                       float r, float g, float b, float a) {
        font.drawText(text, x + 1, y + 1, scale, screenW, screenH, 0, 0, 0, a * 0.6f);
        font.drawText(text, x, y, scale, screenW, screenH, r, g, b, a);
    }

    /** Draw centered text with shadow. */
    protected void drawCenteredTextWithShadow(String text, float y, float scale,
                                               float r, float g, float b, float a) {
        float textW = text.length() * CHAR_W * scale;
        float textX = (screenW - textW) / 2.0f;
        drawTextWithShadow(text, textX, y, scale, r, g, b, a);
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
