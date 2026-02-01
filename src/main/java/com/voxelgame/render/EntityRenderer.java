package com.voxelgame.render;

import com.voxelgame.sim.Entity;
import com.voxelgame.sim.EntityType;
import com.voxelgame.sim.TNTEntity;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Renders mob entities as colored cubes with eyes.
 *
 * Pig: pink body with dark eyes.
 * Zombie: green body with red eyes.
 *
 * Uses the same line shader (uProjection, uView, uColor) as other
 * simple geometry renderers in the game.
 */
public class EntityRenderer {

    // ---- Body colors ----
    private static final float[] PIG_COLOR    = { 0.95f, 0.60f, 0.60f };  // pink
    private static final float[] ZOMBIE_COLOR = { 0.35f, 0.60f, 0.30f };  // green
    private static final float[] BOAT_COLOR   = { 0.55f, 0.35f, 0.15f };  // brown
    private static final float[] CART_COLOR   = { 0.50f, 0.50f, 0.55f };  // gray
    private static final float[] TNT_COLOR    = { 0.85f, 0.20f, 0.15f };  // red
    private static final float[] TNT_FLASH    = { 1.00f, 1.00f, 1.00f };  // white

    // ---- Eye colors ----
    private static final float[] PIG_EYE_COLOR    = { 0.10f, 0.10f, 0.10f };  // black
    private static final float[] ZOMBIE_EYE_COLOR = { 0.95f, 0.10f, 0.10f };  // red

    // ---- Hurt flash color ----
    private static final float[] HURT_TINT = { 1.0f, 0.3f, 0.3f };

    private Shader shader;
    private int vao, vbo;

    public void init() {
        shader = new Shader("shaders/line.vert", "shaders/line.frag");
        buildCube();
    }

    /**
     * Build a unit cube (0,0,0)→(1,1,1) as a triangle mesh.
     */
    private void buildCube() {
        float[] verts = {
                // Front face (z=1)
                0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 0, 1, 1,
                // Back face (z=0)
                1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 1, 0,
                // Top face (y=1)
                0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 0, 0, 1, 0,
                // Bottom face (y=0)
                0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 0, 1,
                // Right face (x=1)
                1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 1, 1, 1, 0, 1, 1, 1,
                // Left face (x=0)
                0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 0, 0, 0, 1, 1, 0, 1, 0,
        };

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(verts.length);
            fb.put(verts).flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        }

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);
    }

    /**
     * Render all entities.
     */
    public void render(Camera camera, int windowW, int windowH, List<Entity> entities) {
        if (entities == null || entities.isEmpty()) return;

        Matrix4f projection = camera.getProjectionMatrix(windowW, windowH);
        Matrix4f cameraView = camera.getViewMatrix();

        shader.bind();
        shader.setMat4("uProjection", projection);

        glEnable(GL_DEPTH_TEST);
        glBindVertexArray(vao);

        for (Entity entity : entities) {
            if (entity.isDead()) continue;

            // Choose colors based on entity type
            float[] bodyColor;
            float[] eyeColor = null;
            boolean hasEyes = true;

            switch (entity.getType()) {
                case PIG -> { bodyColor = PIG_COLOR; eyeColor = PIG_EYE_COLOR; }
                case ZOMBIE -> { bodyColor = ZOMBIE_COLOR; eyeColor = ZOMBIE_EYE_COLOR; }
                case BOAT -> { bodyColor = BOAT_COLOR; hasEyes = false; }
                case MINECART -> { bodyColor = CART_COLOR; hasEyes = false; }
                case TNT -> {
                    // TNT blinks between red and white
                    if (entity instanceof TNTEntity tnt && tnt.isBlinking()) {
                        bodyColor = TNT_FLASH;
                    } else {
                        bodyColor = TNT_COLOR;
                    }
                    hasEyes = false;
                }
                default -> { bodyColor = ZOMBIE_COLOR; eyeColor = ZOMBIE_EYE_COLOR; }
            }

            // Hurt flash: tint red when recently damaged
            float hurtFlash = entity.getHurtTimer() > 0 ? 0.6f : 0.0f;
            float br = bodyColor[0] * (1 - hurtFlash) + HURT_TINT[0] * hurtFlash;
            float bg = bodyColor[1] * (1 - hurtFlash) + HURT_TINT[1] * hurtFlash;
            float bb = bodyColor[2] * (1 - hurtFlash) + HURT_TINT[2] * hurtFlash;

            // Draw body
            drawBody(cameraView, entity, br, bg, bb);

            // Draw eyes (only for mobs)
            if (hasEyes && eyeColor != null) {
                drawEyes(cameraView, entity, eyeColor[0], eyeColor[1], eyeColor[2]);
            }
        }

        glBindVertexArray(0);
        shader.unbind();
    }

    /**
     * Draw the entity body as a scaled, rotated cube.
     */
    private void drawBody(Matrix4f cameraView, Entity entity, float r, float g, float b) {
        float hw = entity.getHalfWidth();
        float h = entity.getHeight();
        float w = hw * 2;

        Matrix4f viewModel = new Matrix4f(cameraView);
        // Position at entity feet
        viewModel.translate(entity.getX(), entity.getY(), entity.getZ());
        // Move to center for rotation
        viewModel.translate(0, h / 2, 0);
        // Rotate by yaw
        viewModel.rotateY((float) Math.toRadians(entity.getYaw()));
        // Move back to corner of unit cube
        viewModel.translate(-hw, -h / 2, -hw);
        // Scale to entity size
        viewModel.scale(w, h, w);

        shader.setMat4("uView", viewModel);
        shader.setVec4("uColor", r, g, b, 1.0f);

        glDrawArrays(GL_TRIANGLES, 0, 36);
    }

    /**
     * Draw two eyes on the entity's front face.
     * Eyes are small cubes positioned based on the entity's yaw rotation.
     */
    private void drawEyes(Matrix4f cameraView, Entity entity, float r, float g, float b) {
        float eyeSize;
        float eyeHeight; // relative to entity height
        float eyeSpacing; // from center

        switch (entity.getType()) {
            case PIG -> {
                eyeSize = 0.07f;
                eyeHeight = 0.65f;
                eyeSpacing = entity.getHalfWidth() * 0.35f;
            }
            default -> {
                // Zombie and other mob types
                eyeSize = 0.08f;
                eyeHeight = 0.82f;
                eyeSpacing = entity.getHalfWidth() * 0.40f;
            }
        }

        float eyeY = entity.getY() + entity.getHeight() * eyeHeight;
        float yawRad = (float) Math.toRadians(entity.getYaw());
        float sin = (float) Math.sin(yawRad);
        float cos = (float) Math.cos(yawRad);

        // Front offset (in front of entity face)
        float frontDist = entity.getHalfWidth() + 0.01f;
        float frontX = sin * frontDist;
        float frontZ = cos * frontDist;

        // Right vector (perpendicular to front direction)
        float rightX = cos;
        float rightZ = -sin;

        for (int side = -1; side <= 1; side += 2) {
            float ex = entity.getX() + frontX + rightX * eyeSpacing * side;
            float ey = eyeY;
            float ez = entity.getZ() + frontZ + rightZ * eyeSpacing * side;

            Matrix4f eyeModel = new Matrix4f(cameraView);
            eyeModel.translate(ex - eyeSize / 2, ey - eyeSize / 2, ez - eyeSize / 2);
            eyeModel.scale(eyeSize);

            shader.setMat4("uView", eyeModel);
            shader.setVec4("uColor", r, g, b, 1.0f);

            glDrawArrays(GL_TRIANGLES, 0, 36);
        }
    }

    public void cleanup() {
        if (vbo != 0) glDeleteBuffers(vbo);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (shader != null) shader.cleanup();
    }
}
