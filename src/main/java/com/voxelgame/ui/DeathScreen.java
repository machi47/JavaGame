package com.voxelgame.ui;

import com.voxelgame.render.Shader;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Full-screen death overlay.
 * Shows a semi-transparent red vignette with "You Died!" title
 * and "Press [R] to Respawn" prompt.
 *
 * Rendering order: overlay quad first (darkened red), then text on top.
 */
public class DeathScreen {

    private Shader uiShader;
    private int quadVao, quadVbo;
    private final BitmapFont font;

    // Animation
    private float fadeAlpha = 0.0f;
    private static final float FADE_SPEED = 2.5f; // seconds to full opacity

    public DeathScreen(BitmapFont font) {
        this.font = font;
    }

    public void init() {
        uiShader = new Shader("shaders/ui.vert", "shaders/ui.frag");
        buildQuadVAO();
    }

    private void buildQuadVAO() {
        float[] v = { 0,0, 1,0, 1,1,  0,0, 1,1, 0,1 };
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

    /**
     * Reset fade animation (call when player dies).
     */
    public void reset() {
        fadeAlpha = 0.0f;
    }

    /**
     * Render the death screen overlay.
     * Must be called AFTER all other rendering, with blend enabled and depth test off.
     *
     * @param screenW  screen width in pixels
     * @param screenH  screen height in pixels
     * @param dt       delta time for fade animation
     */
    public void render(int screenW, int screenH, float dt) {
        // Advance fade
        fadeAlpha = Math.min(1.0f, fadeAlpha + FADE_SPEED * dt);
        float alpha = fadeAlpha;

        // ---- Red overlay ----
        uiShader.bind();
        glBindVertexArray(quadVao);

        // Full-screen quad: ortho that maps unit quad to entire screen
        try (MemoryStack stk = MemoryStack.stackPush()) {
            Matrix4f proj = new Matrix4f().ortho(0, 1, 0, 1, -1, 1);
            FloatBuffer fb = stk.mallocFloat(16);
            proj.get(fb);
            glUniformMatrix4fv(
                glGetUniformLocation(uiShader.getProgramId(), "uProjection"),
                false, fb);
        }
        uiShader.setVec4("uColor", 0.4f, 0.0f, 0.0f, 0.6f * alpha);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindVertexArray(0);
        uiShader.unbind();

        // ---- Text ----
        float titleScale = 4.0f;
        float promptScale = 2.0f;
        float charW = 8.0f; // base character width in pixels

        // "You Died!" centered
        String title = "You Died!";
        float titleW = title.length() * charW * titleScale;
        float titleX = (screenW - titleW) / 2.0f;
        float titleY = screenH / 2.0f - 40.0f;

        // Shadow
        font.drawText(title, titleX + 2, titleY + 2, titleScale, screenW, screenH,
                       0.2f, 0.0f, 0.0f, 0.9f * alpha);
        // Title (bright red)
        font.drawText(title, titleX, titleY, titleScale, screenW, screenH,
                       1.0f, 0.2f, 0.2f, 1.0f * alpha);

        // "Press [R] to Respawn" centered below title
        String prompt = "Press [R] to Respawn";
        float promptW = prompt.length() * charW * promptScale;
        float promptX = (screenW - promptW) / 2.0f;
        float promptY = screenH / 2.0f + 20.0f;

        // Blink effect for prompt
        float blink = (float) (Math.sin(System.nanoTime() / 500_000_000.0) * 0.3 + 0.7);

        // Shadow
        font.drawText(prompt, promptX + 1, promptY + 1, promptScale, screenW, screenH,
                       0.0f, 0.0f, 0.0f, 0.7f * alpha);
        // Prompt (white, blinking)
        font.drawText(prompt, promptX, promptY, promptScale, screenW, screenH,
                       1.0f, 1.0f, 1.0f, blink * alpha);
    }

    public void cleanup() {
        if (quadVbo != 0) glDeleteBuffers(quadVbo);
        if (quadVao != 0) glDeleteVertexArrays(quadVao);
        if (uiShader != null) uiShader.cleanup();
    }
}
