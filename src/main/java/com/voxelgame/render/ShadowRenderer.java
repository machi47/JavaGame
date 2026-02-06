package com.voxelgame.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Cascaded Shadow Map (CSM) renderer for sun/moon shadows.
 * 
 * Uses multiple shadow map cascades at different distances for quality:
 * - Cascade 0: Near (0-16 blocks) - high detail shadows
 * - Cascade 1: Mid (16-64 blocks) - medium detail
 * - Cascade 2: Far (64-256 blocks) - low detail, large area
 * 
 * Each cascade renders the scene from the sun's perspective into a depth texture.
 * The main shader samples these to determine if surfaces are in shadow.
 */
public class ShadowRenderer {

    /** Shadow map resolution (same for all cascades for simplicity). */
    private static final int SHADOW_MAP_SIZE = 2048;
    
    /** Number of shadow cascades. */
    public static final int NUM_CASCADES = 3;
    
    /** Cascade split distances (in blocks from camera). */
    private static final float[] CASCADE_SPLITS = {16.0f, 64.0f, 256.0f};
    
    /** Shadow FBOs - one per cascade. */
    private int[] shadowFBOs = new int[NUM_CASCADES];
    
    /** Shadow depth textures - one per cascade. */
    private int[] shadowTextures = new int[NUM_CASCADES];
    
    /** Light-space view-projection matrices for each cascade. */
    private Matrix4f[] lightViewProj = new Matrix4f[NUM_CASCADES];
    
    /** Shadow shader for depth-only rendering. */
    private Shader shadowShader;
    
    /** Whether shadow system is initialized. */
    private boolean initialized = false;
    
    /** Whether shadows are enabled (disabled at night). */
    private boolean shadowsEnabled = true;

    /**
     * Initialize shadow FBOs, textures, and shader.
     */
    public void init() {
        // Create shadow shader
        shadowShader = new Shader("shaders/shadow.vert", "shaders/shadow.frag");
        
        // Initialize matrices
        for (int i = 0; i < NUM_CASCADES; i++) {
            lightViewProj[i] = new Matrix4f();
        }
        
        // Create FBOs and depth textures for each cascade
        for (int i = 0; i < NUM_CASCADES; i++) {
            shadowFBOs[i] = glGenFramebuffers();
            shadowTextures[i] = glGenTextures();
            
            // Configure depth texture
            glBindTexture(GL_TEXTURE_2D, shadowTextures[i]);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24,
                    SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, 0,
                    GL_DEPTH_COMPONENT, GL_FLOAT, (java.nio.FloatBuffer) null);
            
            // Shadow map filtering - use linear for PCF
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            
            // Clamp to border (samples outside map return 1.0 = not in shadow)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
            float[] borderColor = {1.0f, 1.0f, 1.0f, 1.0f};
            glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, borderColor);
            
            // Enable hardware shadow comparison (for sampler2DShadow)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
            
            // Attach to FBO
            glBindFramebuffer(GL_FRAMEBUFFER, shadowFBOs[i]);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                    GL_TEXTURE_2D, shadowTextures[i], 0);
            
            // No color attachment - depth only
            glDrawBuffer(GL_NONE);
            glReadBuffer(GL_NONE);
            
            // Verify FBO is complete
            int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                throw new RuntimeException("Shadow FBO " + i + " incomplete: " + status);
            }
        }
        
        // Restore default framebuffer
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
        
        initialized = true;
        System.out.println("ShadowRenderer initialized: " + NUM_CASCADES + " cascades @ " + SHADOW_MAP_SIZE + "x" + SHADOW_MAP_SIZE);
    }

    /**
     * Update cascade matrices based on camera position and sun direction.
     * 
     * @param camera The player camera
     * @param sunDirection Direction TO the sun (normalized)
     * @param fov Camera field of view in degrees
     * @param aspect Camera aspect ratio
     */
    public void updateCascades(Camera camera, float[] sunDirection, float fov, float aspect) {
        if (!initialized) return;
        
        // Convert sun direction to Vector3f
        Vector3f sunDir = new Vector3f(sunDirection[0], sunDirection[1], sunDirection[2]);
        
        // If sun is below horizon, disable shadows
        if (sunDir.y < 0.05f) {
            shadowsEnabled = false;
            return;
        }
        shadowsEnabled = true;
        
        // Camera frustum parameters
        float near = camera.getNearPlane();
        float tanHalfFov = (float) Math.tan(Math.toRadians(fov / 2.0));
        
        // Camera vectors
        Vector3f camPos = new Vector3f(camera.getPosition());
        Vector3f camFront = new Vector3f(camera.getFront());
        Vector3f camRight = new Vector3f(camera.getRight());
        Vector3f camUp = new Vector3f(camera.getUp());
        
        float prevSplit = near;
        
        for (int i = 0; i < NUM_CASCADES; i++) {
            float split = CASCADE_SPLITS[i];
            
            // Calculate frustum corners for this cascade slice
            Vector3f[] frustumCorners = getFrustumCorners(
                    camPos, camFront, camRight, camUp,
                    prevSplit, split, tanHalfFov, aspect);
            
            // Calculate center and radius of frustum slice
            Vector3f center = new Vector3f();
            for (Vector3f corner : frustumCorners) {
                center.add(corner);
            }
            center.div(8.0f);
            
            // Calculate radius (max distance from center to any corner)
            float radius = 0;
            for (Vector3f corner : frustumCorners) {
                float dist = center.distance(corner);
                radius = Math.max(radius, dist);
            }
            
            // Round radius up for stable shadow map (reduces shimmering)
            radius = (float) Math.ceil(radius * 16.0f) / 16.0f;
            
            // Create light view matrix (looking from sun toward center)
            Vector3f lightPos = new Vector3f(center).add(new Vector3f(sunDir).mul(radius * 2.0f));
            
            // Choose stable up vector (avoid gimbal lock when sun is overhead)
            Vector3f lightUp = new Vector3f(0, 1, 0);
            if (Math.abs(sunDir.y) > 0.99f) {
                lightUp = new Vector3f(1, 0, 0);
            }
            
            Matrix4f lightView = new Matrix4f().lookAt(lightPos, center, lightUp);
            
            // Create orthographic projection
            Matrix4f lightProj = new Matrix4f().ortho(
                    -radius, radius,   // left, right
                    -radius, radius,   // bottom, top
                    0.1f, radius * 4.0f // near, far (far enough to catch everything)
            );
            
            // Texel snapping to reduce shadow edge shimmering during camera movement
            Matrix4f lightVP = new Matrix4f(lightProj).mul(lightView);
            Vector4f shadowOrigin = new Vector4f(0, 0, 0, 1).mul(lightVP);
            shadowOrigin.mul(SHADOW_MAP_SIZE / 2.0f);
            
            float offsetX = (float) Math.round(shadowOrigin.x) - shadowOrigin.x;
            float offsetY = (float) Math.round(shadowOrigin.y) - shadowOrigin.y;
            offsetX /= SHADOW_MAP_SIZE / 2.0f;
            offsetY /= SHADOW_MAP_SIZE / 2.0f;
            
            lightProj.m30(lightProj.m30() + offsetX);
            lightProj.m31(lightProj.m31() + offsetY);
            
            // Store final matrix
            lightViewProj[i] = new Matrix4f(lightProj).mul(lightView);
            
            prevSplit = split;
        }
    }

    /**
     * Calculate the 8 corners of a frustum slice.
     */
    private Vector3f[] getFrustumCorners(
            Vector3f camPos, Vector3f camFront, Vector3f camRight, Vector3f camUp,
            float nearDist, float farDist, float tanHalfFov, float aspect) {
        
        // Near plane dimensions
        float nearHeight = nearDist * tanHalfFov;
        float nearWidth = nearHeight * aspect;
        
        // Far plane dimensions
        float farHeight = farDist * tanHalfFov;
        float farWidth = farHeight * aspect;
        
        // Near center and far center
        Vector3f nearCenter = new Vector3f(camPos).add(new Vector3f(camFront).mul(nearDist));
        Vector3f farCenter = new Vector3f(camPos).add(new Vector3f(camFront).mul(farDist));
        
        // Calculate 8 corners
        Vector3f[] corners = new Vector3f[8];
        
        // Near plane corners
        corners[0] = new Vector3f(nearCenter)
                .add(new Vector3f(camUp).mul(nearHeight))
                .sub(new Vector3f(camRight).mul(nearWidth));  // top-left
        corners[1] = new Vector3f(nearCenter)
                .add(new Vector3f(camUp).mul(nearHeight))
                .add(new Vector3f(camRight).mul(nearWidth));  // top-right
        corners[2] = new Vector3f(nearCenter)
                .sub(new Vector3f(camUp).mul(nearHeight))
                .sub(new Vector3f(camRight).mul(nearWidth));  // bottom-left
        corners[3] = new Vector3f(nearCenter)
                .sub(new Vector3f(camUp).mul(nearHeight))
                .add(new Vector3f(camRight).mul(nearWidth));  // bottom-right
        
        // Far plane corners
        corners[4] = new Vector3f(farCenter)
                .add(new Vector3f(camUp).mul(farHeight))
                .sub(new Vector3f(camRight).mul(farWidth));   // top-left
        corners[5] = new Vector3f(farCenter)
                .add(new Vector3f(camUp).mul(farHeight))
                .add(new Vector3f(camRight).mul(farWidth));   // top-right
        corners[6] = new Vector3f(farCenter)
                .sub(new Vector3f(camUp).mul(farHeight))
                .sub(new Vector3f(camRight).mul(farWidth));   // bottom-left
        corners[7] = new Vector3f(farCenter)
                .sub(new Vector3f(camUp).mul(farHeight))
                .add(new Vector3f(camRight).mul(farWidth));   // bottom-right
        
        return corners;
    }

    /**
     * Begin shadow pass for a specific cascade.
     * Call this, then render chunks, then call endShadowPass().
     */
    public void beginShadowPass(int cascade) {
        if (!initialized || !shadowsEnabled) return;
        if (cascade < 0 || cascade >= NUM_CASCADES) return;
        
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFBOs[cascade]);
        glViewport(0, 0, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE);
        glClear(GL_DEPTH_BUFFER_BIT);
        
        // Cull front faces to reduce shadow acne
        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT);
        
        // Enable polygon offset to further reduce shadow acne
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(2.0f, 4.0f);
        
        // Bind shadow shader and set matrix
        shadowShader.bind();
        shadowShader.setMat4("uLightViewProj", lightViewProj[cascade]);
    }

    /**
     * End shadow pass. Restores default framebuffer.
     */
    public void endShadowPass() {
        endShadowPass(0);  // Default to screen FBO for backward compatibility
    }
    
    public void endShadowPass(int restoreFBO) {
        if (!initialized) return;
        
        shadowShader.unbind();
        
        // Disable polygon offset
        glDisable(GL_POLYGON_OFFSET_FILL);
        
        // Restore back-face culling
        glCullFace(GL_BACK);
        
        // Restore specified framebuffer (0 = screen, or PostFX scene FBO)
        glBindFramebuffer(GL_FRAMEBUFFER, restoreFBO);
    }

    /**
     * Get shadow texture for a cascade.
     */
    public int getShadowTexture(int cascade) {
        if (cascade < 0 || cascade >= NUM_CASCADES) return 0;
        return shadowTextures[cascade];
    }

    /**
     * Get light-space view-projection matrix for a cascade.
     */
    public Matrix4f getLightViewProj(int cascade) {
        if (cascade < 0 || cascade >= NUM_CASCADES) return new Matrix4f();
        return lightViewProj[cascade];
    }

    /**
     * Get cascade split distances.
     */
    public float[] getCascadeSplits() {
        return CASCADE_SPLITS;
    }

    /**
     * Get shadow map size.
     */
    public int getShadowMapSize() {
        return SHADOW_MAP_SIZE;
    }

    /**
     * Check if shadows are currently enabled.
     * Shadows are disabled at night when sun is below horizon.
     */
    public boolean isShadowsEnabled() {
        return initialized && shadowsEnabled;
    }

    /**
     * Get the shadow shader (for depth-only rendering).
     */
    public Shader getShadowShader() {
        return shadowShader;
    }

    /**
     * Cleanup GPU resources.
     */
    public void cleanup() {
        if (!initialized) return;
        
        for (int i = 0; i < NUM_CASCADES; i++) {
            if (shadowFBOs[i] != 0) {
                glDeleteFramebuffers(shadowFBOs[i]);
            }
            if (shadowTextures[i] != 0) {
                glDeleteTextures(shadowTextures[i]);
            }
        }
        
        if (shadowShader != null) {
            shadowShader.cleanup();
        }
        
        initialized = false;
    }
}
