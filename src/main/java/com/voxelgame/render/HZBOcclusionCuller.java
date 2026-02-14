package com.voxelgame.render;

import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.WorldConstants;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.opengl.GL33.*;
import org.lwjgl.BufferUtils;

/**
 * Hierarchical Z-Buffer (HZB) based occlusion culling.
 *
 * Uses the depth buffer from the previous frame to test if chunks
 * are occluded by geometry that was already rendered.
 *
 * Process:
 * 1. After opaque pass, downsample depth buffer into mipmap chain
 * 2. For each candidate chunk, project AABB to screen space
 * 3. Select appropriate mip level based on AABB screen size
 * 4. Sample HZB conservatively (farthest depth in region)
 * 5. If chunk is fully behind that depth -> occluded
 *
 * IMPORTANT: Only applies to opaque geometry. Transparent chunks
 * (water, glass) are NOT culled by HZB since they could be visible
 * through openings in opaque geometry.
 */
public class HZBOcclusionCuller {

    /** HZB texture resolution (power of 2, full res = window size). */
    private int hzbWidth;
    private int hzbHeight;

    /** Number of mip levels in the HZB pyramid. */
    private int mipLevels;

    /** HZB texture with mipmap chain. */
    private int hzbTexture;

    /** FBO for HZB generation. */
    private int hzbFBO;

    /** Shader for HZB mipmap generation. */
    private Shader hzbDownsampleShader;

    /** Full-screen quad VAO for downsample passes. */
    private int quadVAO;
    private int quadVBO;

    /** Set of chunks that were visible last frame (temporal coherence). */
    private final Set<ChunkPos> lastFrameVisible = new HashSet<>();

    /** Set of chunks culled by HZB this frame. */
    private final Set<ChunkPos> culledThisFrame = new HashSet<>();

    /** Whether HZB system is initialized. */
    private boolean initialized = false;

    /** Whether HZB culling is enabled. */
    private boolean enabled = true;

    /** Debug stats */
    private int testedCount = 0;
    private int culledCount = 0;
    private int passedCount = 0;

    /**
     * Initialize the HZB system.
     * Call after OpenGL context is created.
     *
     * @param width  Window width
     * @param height Window height
     */
    public void init(int width, int height) {
        // Use power-of-2 dimensions for efficient mipmap chain
        hzbWidth = nextPowerOfTwo(width);
        hzbHeight = nextPowerOfTwo(height);
        mipLevels = (int) (Math.log(Math.max(hzbWidth, hzbHeight)) / Math.log(2)) + 1;

        // Create HZB texture with mipmap storage
        hzbTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, hzbTexture);

        // Allocate all mip levels
        for (int level = 0; level < mipLevels; level++) {
            int w = Math.max(1, hzbWidth >> level);
            int h = Math.max(1, hzbHeight >> level);
            glTexImage2D(GL_TEXTURE_2D, level, GL_R32F, w, h, 0, GL_RED, GL_FLOAT, (FloatBuffer) null);
        }

        // Filtering - use NEAREST for conservative depth sampling
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, mipLevels - 1);

        // Create FBO for mipmap generation
        hzbFBO = glGenFramebuffers();

        // Create downsample shader
        hzbDownsampleShader = new Shader("shaders/hzb_downsample.vert", "shaders/hzb_downsample.frag");

        // Create fullscreen quad
        createFullscreenQuad();

        initialized = true;
        System.out.println("[HZB] Initialized: " + hzbWidth + "x" + hzbHeight + " with " + mipLevels + " mip levels");
    }

    /**
     * Generate HZB mipmap pyramid from the current depth buffer.
     * Call this after the opaque pass completes.
     *
     * @param depthTexture Source depth texture from scene FBO
     * @param projMatrix   Current projection matrix
     */
    public void generateHZB(int depthTexture, Matrix4f projMatrix) {
        if (!initialized || !enabled) return;

        glBindFramebuffer(GL_FRAMEBUFFER, hzbFBO);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);

        hzbDownsampleShader.bind();

        // Mip 0: Copy from depth buffer (linearize depth)
        glViewport(0, 0, hzbWidth, hzbHeight);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, hzbTexture, 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        hzbDownsampleShader.setInt("uSourceTex", 0);
        hzbDownsampleShader.setInt("uSourceMip", -1); // -1 signals depth copy mode
        hzbDownsampleShader.setVec2("uTexelSize", 1.0f / hzbWidth, 1.0f / hzbHeight);

        // Pass projection matrix for depth linearization
        float near = 0.1f;  // Should match camera
        float far = projMatrix.m32() / (projMatrix.m22() - 1.0f);
        hzbDownsampleShader.setFloat("uNear", near);
        hzbDownsampleShader.setFloat("uFar", far);

        drawFullscreenQuad();

        // Generate mip chain
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, hzbTexture);

        for (int level = 1; level < mipLevels; level++) {
            int w = Math.max(1, hzbWidth >> level);
            int h = Math.max(1, hzbHeight >> level);

            glViewport(0, 0, w, h);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, hzbTexture, level);

            // Sample from previous mip level
            hzbDownsampleShader.setInt("uSourceMip", level - 1);
            hzbDownsampleShader.setVec2("uTexelSize", 1.0f / w, 1.0f / h);

            drawFullscreenQuad();
        }

        hzbDownsampleShader.unbind();
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glEnable(GL_DEPTH_TEST);
    }

    /**
     * Test if a chunk is occluded by the HZB.
     * Uses temporal coherence: chunks visible last frame skip HZB test.
     *
     * @param pos        Chunk position
     * @param viewProj   Combined view-projection matrix
     * @param camX, camZ Camera chunk coordinates
     * @return true if chunk is visible (NOT occluded), false if occluded
     */
    public boolean testChunkVisibility(ChunkPos pos, Matrix4f viewProj, int camX, int camZ) {
        if (!initialized || !enabled) return true;

        testedCount++;

        // Temporal coherence: visible last frame -> visible this frame
        // This provides one-frame stability and handles fast movement
        if (lastFrameVisible.contains(pos)) {
            passedCount++;
            return true;
        }

        // Build chunk AABB in world space
        float minX = pos.x() * WorldConstants.CHUNK_SIZE;
        float minY = 0;
        float minZ = pos.z() * WorldConstants.CHUNK_SIZE;
        float maxX = minX + WorldConstants.CHUNK_SIZE;
        float maxY = WorldConstants.WORLD_HEIGHT;
        float maxZ = minZ + WorldConstants.CHUNK_SIZE;

        // Project all 8 corners to clip space
        float minScreenX = Float.MAX_VALUE;
        float maxScreenX = -Float.MAX_VALUE;
        float minScreenY = Float.MAX_VALUE;
        float maxScreenY = -Float.MAX_VALUE;
        float minZ_ndc = Float.MAX_VALUE;
        boolean anyBehind = false;
        boolean anyInFront = false;

        float[][] corners = {
            {minX, minY, minZ}, {maxX, minY, minZ},
            {minX, maxY, minZ}, {maxX, maxY, minZ},
            {minX, minY, maxZ}, {maxX, minY, maxZ},
            {minX, maxY, maxZ}, {maxX, maxY, maxZ}
        };

        Vector4f clipPos = new Vector4f();
        for (float[] corner : corners) {
            clipPos.set(corner[0], corner[1], corner[2], 1.0f);
            clipPos.mul(viewProj);

            // Check if behind camera
            if (clipPos.w <= 0) {
                anyBehind = true;
                continue;
            }
            anyInFront = true;

            // Perspective divide
            float ndcX = clipPos.x / clipPos.w;
            float ndcY = clipPos.y / clipPos.w;
            float ndcZ = clipPos.z / clipPos.w;

            // Convert to screen space [0, 1]
            float screenX = (ndcX + 1.0f) * 0.5f;
            float screenY = (ndcY + 1.0f) * 0.5f;

            minScreenX = Math.min(minScreenX, screenX);
            maxScreenX = Math.max(maxScreenX, screenX);
            minScreenY = Math.min(minScreenY, screenY);
            maxScreenY = Math.max(maxScreenY, screenY);
            minZ_ndc = Math.min(minZ_ndc, ndcZ);
        }

        // If any corner is behind camera, chunk might be visible
        // (conservative - could be partially behind)
        if (anyBehind) {
            if (!anyInFront) {
                // Entirely behind camera - not visible
                culledCount++;
                culledThisFrame.add(pos);
                return false;
            }
            // Straddles near plane - assume visible
            passedCount++;
            return true;
        }

        // Clamp to screen bounds
        minScreenX = Math.max(0, Math.min(1, minScreenX));
        maxScreenX = Math.max(0, Math.min(1, maxScreenX));
        minScreenY = Math.max(0, Math.min(1, minScreenY));
        maxScreenY = Math.max(0, Math.min(1, maxScreenY));

        // Calculate screen-space size
        float screenWidth = maxScreenX - minScreenX;
        float screenHeight = maxScreenY - minScreenY;

        // If very small on screen, probably visible (sub-pixel)
        if (screenWidth < 0.001f || screenHeight < 0.001f) {
            passedCount++;
            return true;
        }

        // Select mip level based on screen-space size
        float maxDim = Math.max(screenWidth * hzbWidth, screenHeight * hzbHeight);
        int mipLevel = Math.min(mipLevels - 1, (int) Math.ceil(Math.log(maxDim) / Math.log(2)));

        // Sample HZB at the corners of the screen-space rect
        float hzbDepth = sampleHZBConservative(minScreenX, minScreenY, maxScreenX, maxScreenY, mipLevel);

        // Compare chunk minimum depth to HZB depth
        // If chunk is entirely behind the HZB depth, it's occluded
        if (minZ_ndc > hzbDepth) {
            culledCount++;
            culledThisFrame.add(pos);
            return false;
        }

        passedCount++;
        return true;
    }

    /**
     * Sample HZB conservatively over a screen-space rectangle.
     * Returns the farthest (maximum) depth in the region.
     */
    private float sampleHZBConservative(float minU, float minV, float maxU, float maxV, int mipLevel) {
        // For now, use simple corner sampling with MAX
        // A full implementation would use compute shaders for better accuracy
        // TODO: Implement proper hierarchical testing

        int mipW = Math.max(1, hzbWidth >> mipLevel);
        int mipH = Math.max(1, hzbHeight >> mipLevel);

        // Sample 4 corners at the selected mip level
        int x0 = (int) (minU * mipW);
        int y0 = (int) (minV * mipH);
        int x1 = Math.min(mipW - 1, (int) (maxU * mipW));
        int y1 = Math.min(mipH - 1, (int) (maxV * mipH));

        // Read depth values from HZB texture
        // Note: This requires glGetTexImage which is slow - in production
        // you'd want to do this in a compute shader
        // For now, use a cached approach or skip precise testing

        // Simplified: return a depth that assumes the chunk is visible
        // This makes HZB conservative (no false culling)
        // Full implementation would use GPU queries
        return 1.0f; // TODO: Implement actual sampling
    }

    /**
     * Begin frame - reset stats and prepare for new tests.
     */
    public void beginFrame() {
        testedCount = 0;
        culledCount = 0;
        passedCount = 0;
        culledThisFrame.clear();
    }

    /**
     * End frame - update temporal coherence set.
     */
    public void endFrame(Set<ChunkPos> visibleChunks) {
        lastFrameVisible.clear();
        lastFrameVisible.addAll(visibleChunks);
    }

    /**
     * Get chunks culled by HZB this frame.
     */
    public Set<ChunkPos> getCulledChunks() {
        return culledThisFrame;
    }

    /**
     * Resize HZB buffers when window size changes.
     */
    public void resize(int width, int height) {
        if (!initialized) return;

        // Check if size actually changed significantly
        int newWidth = nextPowerOfTwo(width);
        int newHeight = nextPowerOfTwo(height);

        if (newWidth != hzbWidth || newHeight != hzbHeight) {
            cleanup();
            init(width, height);
        }
    }

    private void createFullscreenQuad() {
        float[] vertices = {
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 1.0f,
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 1.0f,
            -1.0f,  1.0f, 0.0f, 1.0f
        };

        quadVAO = glGenVertexArrays();
        quadVBO = glGenBuffers();

        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 16, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 16, 8);

        glBindVertexArray(0);
    }

    private void drawFullscreenQuad() {
        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
    }

    private int nextPowerOfTwo(int n) {
        n--;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        return n + 1;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getTestedCount() { return testedCount; }
    public int getCulledCount() { return culledCount; }
    public int getPassedCount() { return passedCount; }

    public void cleanup() {
        if (hzbTexture != 0) {
            glDeleteTextures(hzbTexture);
            hzbTexture = 0;
        }
        if (hzbFBO != 0) {
            glDeleteFramebuffers(hzbFBO);
            hzbFBO = 0;
        }
        if (quadVAO != 0) {
            glDeleteVertexArrays(quadVAO);
            quadVAO = 0;
        }
        if (quadVBO != 0) {
            glDeleteBuffers(quadVBO);
            quadVBO = 0;
        }
        if (hzbDownsampleShader != null) {
            hzbDownsampleShader.cleanup();
            hzbDownsampleShader = null;
        }
        initialized = false;
    }
}
