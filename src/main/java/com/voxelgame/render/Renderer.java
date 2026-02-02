package com.voxelgame.render;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.World;
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

    /** Update sun brightness and fog color from world time. Call once per frame. */
    public void updateLighting(WorldTime worldTime) {
        if (worldTime != null) {
            this.sunBrightness = worldTime.getSunBrightness();
            this.fogColor = worldTime.getSkyColor();
        }
    }

    public void render(Camera camera, int windowWidth, int windowHeight) {
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

        // Bind shader and set shared uniforms
        blockShader.bind();
        blockShader.setMat4("uProjection", projection);
        blockShader.setMat4("uView", view);
        blockShader.setInt("uAtlas", 0);
        blockShader.setFloat("uSunBrightness", sunBrightness);
        blockShader.setVec3("uCameraPos", camera.getPosition());
        blockShader.setVec3("uFogColor", fogColor[0], fogColor[1], fogColor[2]);
        blockShader.setFloat("uFogStart", fogStart);
        blockShader.setFloat("uFogEnd", fogEnd);

        atlas.bind(0);

        renderedChunks = 0;
        culledChunks = 0;

        // ---- Pass 1: Opaque geometry (all LOD levels) ----
        blockShader.setFloat("uAlpha", 1.0f);

        for (var entry : world.getChunkMap().entrySet()) {
            ChunkPos pos = entry.getKey();
            Chunk chunk = entry.getValue();

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

        // ---- Pass 2: Transparent geometry (water) — LOD 0 only ----
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);       // don't write to depth buffer
        glDisable(GL_CULL_FACE);  // render both sides of water

        blockShader.setFloat("uAlpha", WATER_ALPHA);

        for (var entry : world.getChunkMap().entrySet()) {
            ChunkPos pos = entry.getKey();
            Chunk chunk = entry.getValue();

            if (!frustum.isChunkVisible(pos.x(), pos.z())) continue;

            // Only render transparent for LOD 0 chunks
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
