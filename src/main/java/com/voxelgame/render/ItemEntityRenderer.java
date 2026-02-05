package com.voxelgame.render;

import com.voxelgame.sim.ItemEntity;
import com.voxelgame.world.Block;
import com.voxelgame.world.Blocks;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Renders dropped item entities as textured billboards floating in the world.
 * Uses the texture atlas for consistent visuals with inventory/hotbar.
 */
public class ItemEntityRenderer {

    private TextureAtlas atlas;
    private Shader shader;
    private int vao, vbo;

    public void init() {
        shader = new Shader("shaders/item_billboard.vert", "shaders/item_billboard.frag");
        buildQuad();
    }

    /**
     * Set the texture atlas for rendering item textures.
     */
    public void setAtlas(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    /**
     * Build a unit quad for billboard rendering.
     * Vertices are centered: x in [-0.5, 0.5], y in [0, 1] (grounded)
     * Each vertex has position (x, y, z) and UV (u, v).
     */
    private void buildQuad() {
        // Quad vertices: position (x, y, z) + UV (u, v)
        // x: -0.5 to 0.5 (centered horizontally)
        // y: 0 to 1 (bottom to top, grounded)
        // z: 0 (flat billboard)
        float[] verts = {
            // Triangle 1
            -0.5f, 0.0f, 0.0f,  0.0f, 1.0f,  // bottom-left
             0.5f, 0.0f, 0.0f,  1.0f, 1.0f,  // bottom-right
             0.5f, 1.0f, 0.0f,  1.0f, 0.0f,  // top-right
            // Triangle 2
            -0.5f, 0.0f, 0.0f,  0.0f, 1.0f,  // bottom-left
             0.5f, 1.0f, 0.0f,  1.0f, 0.0f,  // top-right
            -0.5f, 1.0f, 0.0f,  0.0f, 0.0f,  // top-left
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

        int stride = 5 * Float.BYTES;
        // Position attribute (location 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        // UV attribute (location 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
    }

    /**
     * Render all item entities as textured billboards.
     */
    public void render(Camera camera, int windowW, int windowH, List<ItemEntity> itemList) {
        if (itemList == null || itemList.isEmpty()) return;
        if (atlas == null) return; // Can't render without atlas

        Matrix4f projection = camera.getProjectionMatrix(windowW, windowH);
        Matrix4f view = camera.getViewMatrix();

        shader.bind();
        shader.setMat4("uProjection", projection);
        shader.setMat4("uView", view);

        // Bind atlas texture
        glActiveTexture(GL_TEXTURE0);
        atlas.bind(0);
        shader.setInt("uAtlas", 0);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Disable backface culling so billboard is visible from both sides
        glDisable(GL_CULL_FACE);

        glBindVertexArray(vao);

        float size = ItemEntity.SIZE;

        for (ItemEntity item : itemList) {
            if (item.isDead()) continue;

            int blockId = item.getBlockId();
            Block block = Blocks.get(blockId);
            int tileIndex = block.getTextureIndex(0);
            
            if (tileIndex < 0) continue; // Skip invalid textures

            // Get UV coordinates from atlas
            float[] uv = atlas.getUV(tileIndex);
            
            // Set item position (use item center, slightly offset up)
            shader.setVec3("uItemPos", item.getX(), item.getY(), item.getZ());
            shader.setFloat("uSize", size);
            shader.setFloat("uRotation", (float) Math.toRadians(item.getRotation()));

            // Calculate pulsing brightness
            float pulse = 0.5f + 0.5f * (float) Math.sin(item.getAge() * 4.0f);
            shader.setFloat("uBrightness", pulse);

            // Override UVs in the shader by modifying the quad's UV lookup
            // We need to transform UVs: the quad uses 0-1, we need u0-u1 and v0-v1
            // Set the UV rect as uniforms
            setAtlasUVs(uv);

            glDrawArrays(GL_TRIANGLES, 0, 6);
        }

        glBindVertexArray(0);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        shader.unbind();
    }

    /**
     * Dynamically update the quad's UVs for the current item's texture.
     * Since we can't easily modify UVs per-draw, we use a different approach:
     * Rebuild the VBO with the correct UVs for each item.
     * 
     * Note: For better performance, batch rendering could be implemented later.
     */
    private void setAtlasUVs(float[] uv) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        
        // Rebuild quad with atlas UVs
        float[] verts = {
            // Triangle 1
            -0.5f, 0.0f, 0.0f,  u0, v1,  // bottom-left
             0.5f, 0.0f, 0.0f,  u1, v1,  // bottom-right
             0.5f, 1.0f, 0.0f,  u1, v0,  // top-right
            // Triangle 2
            -0.5f, 0.0f, 0.0f,  u0, v1,  // bottom-left
             0.5f, 1.0f, 0.0f,  u1, v0,  // top-right
            -0.5f, 1.0f, 0.0f,  u0, v0,  // top-left
        };

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        try (MemoryStack stk = MemoryStack.stackPush()) {
            FloatBuffer fb = stk.mallocFloat(verts.length);
            fb.put(verts).flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, fb);
        }
    }

    public void cleanup() {
        if (vbo != 0) glDeleteBuffers(vbo);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (shader != null) shader.cleanup();
    }
}
