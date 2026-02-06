package com.voxelgame.render;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;
import com.voxelgame.world.WorldTime;
import com.voxelgame.world.lod.LODConfig;
import com.voxelgame.world.lod.LODLevel;
import com.voxelgame.world.mesh.ChunkMesh;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Main rendering coordinator. Binds shader, sets uniforms,
 * renders visible chunk meshes with frustum culling.
 *
 * Supports multi-tier LOD rendering:
 * - LOD 0: Full detail opaque + transparent passes
 * - LOD 1-3: Opaque-only simplified meshes
 * - Dynamic fog distance based on LOD config
 *
 * Uses two passes:
 *   1. Opaque pass  — depth write ON, blend OFF
 *   2. Transparent pass — depth write OFF, blend ON (water, etc.)
 */
public class Renderer {

    private static final float WATER_ALPHA = 0.55f;

    private Shader blockShader;
    private TextureAtlas atlas;
    private Frustum frustum;
    private final World world;

    /** Current sun brightness for time-of-day lighting. Updated each frame. */
    private float sunBrightness = 1.0f;

    /** Current fog/sky color. Updated each frame from WorldTime. */
    private float[] fogColor = {0.53f, 0.68f, 0.90f};
    
    /** Current sun direction vector. Updated each frame from WorldTime. */
    private float[] sunDirection = {0.0f, 1.0f, 0.0f};
    
    /** Current sun intensity for directional lighting. Updated each frame from WorldTime. */
    private float sunIntensity = 1.0f;

    /** LOD configuration — controls fog distances. May be null if LOD not initialized. */
    private LODConfig lodConfig;

    // ---- Render stats ----
    private int renderedChunks;
    private int culledChunks;

    public Renderer(World world) {
        this.world = world;
    }

    public void init() {
        blockShader = new Shader("shaders/block.vert", "shaders/block.frag");
        atlas = new TextureAtlas();
        atlas.init();
        frustum = new Frustum();
    }

    /** Set the LOD config for dynamic fog distances. */
    public void setLodConfig(LODConfig lodConfig) {
        this.lodConfig = lodConfig;
    }

    /** Update sun brightness, fog color, sun direction, and sun intensity from world time. Call once per frame. */
    public void updateLighting(WorldTime worldTime) {
        if (worldTime != null) {
            this.sunBrightness = worldTime.getSunBrightness();
            this.fogColor = worldTime.getSkyColor();
            this.sunDirection = worldTime.getSunDirection();
            this.sunIntensity = worldTime.getSunIntensity();
        }
    }

    public void render(Camera camera, int windowWidth, int windowHeight) {
        // Sync camera far plane with LOD config (prevents frustum from extending way beyond render distance)
        if (lodConfig != null) {
            camera.setFarPlane(lodConfig.getFarPlane());
        }

        Matrix4f projection = camera.getProjectionMatrix(windowWidth, windowHeight);
        Matrix4f view = camera.getViewMatrix();

        // Update frustum
        Matrix4f projView = new Matrix4f(projection).mul(view);
        frustum.update(projView);

        // Compute fog distances from LOD config
        float fogStart, fogEnd;
        if (lodConfig != null) {
            fogStart = lodConfig.getFogStart();
            fogEnd = lodConfig.getFogEnd();
        } else {
            fogStart = 80.0f;
            fogEnd = 128.0f;
        }

        // Pre-compute distance culling threshold (in chunk coordinates)
        // Chunks beyond maxRenderDistance + 1 are fully fogged and don't need rendering
        float maxChunkDist = lodConfig != null ? lodConfig.getMaxRenderDistance() + 1.0f : 20.0f;
        float maxChunkDistSq = maxChunkDist * maxChunkDist;
        float camCX = camera.getPosition().x / WorldConstants.CHUNK_SIZE;
        float camCZ = camera.getPosition().z / WorldConstants.CHUNK_SIZE;

        // Bind shader and set shared uniforms
        blockShader.bind();
        blockShader.setMat4("uProjection", projection);
        blockShader.setMat4("uView", view);
        blockShader.setInt("uAtlas", 0);
        blockShader.setFloat("uSunBrightness", sunBrightness);
        blockShader.setVec3("uSunDirection", sunDirection[0], sunDirection[1], sunDirection[2]);
        blockShader.setFloat("uSunIntensity", sunIntensity);
        blockShader.setVec3("uCameraPos", camera.getPosition());
        blockShader.setVec3("uFogColor", fogColor[0], fogColor[1], fogColor[2]);
        blockShader.setFloat("uFogStart", fogStart);
        blockShader.setFloat("uFogEnd", fogEnd);
        
        // Unified lighting uniforms - sky color for shader-side RGB computation
        // Use fog color as sky color (they're derived from the same source in WorldTime)
        blockShader.setVec3("uSkyColor", fogColor[0], fogColor[1], fogColor[2]);
        // Sky intensity: modulate based on sun brightness (brighter sky during day)
        // At midday (sunBrightness=0.65): skyIntensity ~0.4
        // At night (sunBrightness=0.05): skyIntensity ~0.05
        float skyIntensity = 0.1f + sunBrightness * 0.5f;
        blockShader.setFloat("uSkyIntensity", skyIntensity);

        atlas.bind(0);

        renderedChunks = 0;
        culledChunks = 0;

        // ---- Pass 1: Opaque geometry (all LOD levels) ----
        // Includes alpha-discard geometry (torches, flowers, rails) which need both-side rendering.
        // Disabling cull face here is safe since solid block faces are single-sided anyway.
        blockShader.setFloat("uAlpha", 1.0f);
        glDisable(GL_CULL_FACE);

        for (var entry : world.getChunkMap().entrySet()) {
            ChunkPos pos = entry.getKey();
            Chunk chunk = entry.getValue();

            // Distance cull — skip chunks beyond fog end (fully fogged, no point rendering)
            float cdx = pos.x() + 0.5f - camCX;
            float cdz = pos.z() + 0.5f - camCZ;
            if (cdx * cdx + cdz * cdz > maxChunkDistSq) {
                culledChunks++;
                continue;
            }

            if (!frustum.isChunkVisible(pos.x(), pos.z())) {
                culledChunks++;
                continue;
            }

            // Use LOD-appropriate mesh
            ChunkMesh mesh = chunk.getRenderMesh();
            if (mesh != null && !mesh.isEmpty()) {
                mesh.draw();
                renderedChunks++;
            }
        }

        // Re-enable cull face after opaque pass (was disabled for cross-billboard geometry)
        glEnable(GL_CULL_FACE);

        // ---- Pass 2: Transparent geometry (water only now) — LOD 0 only ----
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);       // don't write to depth buffer
        glDisable(GL_CULL_FACE);  // render both sides of water

        blockShader.setFloat("uAlpha", WATER_ALPHA);

        for (var entry : world.getChunkMap().entrySet()) {
            Chunk chunk = entry.getValue();

            // Skip non-LOD0 chunks early — they never have transparent meshes
            if (chunk.getCurrentLOD() != com.voxelgame.world.lod.LODLevel.LOD_0) continue;

            ChunkPos pos = entry.getKey();
            if (!frustum.isChunkVisible(pos.x(), pos.z())) continue;

            ChunkMesh transMesh = chunk.getRenderTransparentMesh();
            if (transMesh != null && !transMesh.isEmpty()) {
                transMesh.draw();
            }
        }

        // Restore state
        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);

        blockShader.unbind();
    }

    public TextureAtlas getAtlas() { return atlas; }
    public int getRenderedChunks() { return renderedChunks; }
    public int getCulledChunks() { return culledChunks; }

    public void cleanup() {
        if (blockShader != null) blockShader.cleanup();
        if (atlas != null) atlas.cleanup();
    }
}
