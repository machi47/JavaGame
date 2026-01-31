package com.voxelgame.render;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.ChunkPos;
import com.voxelgame.world.World;
import org.joml.Matrix4f;

/**
 * Main rendering coordinator. Binds shader, sets uniforms,
 * renders visible chunk meshes with frustum culling.
 */
public class Renderer {

    private Shader blockShader;
    private TextureAtlas atlas;
    private Frustum frustum;
    private final World world;

    public Renderer(World world) {
        this.world = world;
    }

    public void init() {
        blockShader = new Shader("shaders/block.vert", "shaders/block.frag");
        atlas = new TextureAtlas();
        atlas.init();
        frustum = new Frustum();
    }

    public void render(Camera camera, int windowWidth, int windowHeight) {
        Matrix4f projection = camera.getProjectionMatrix(windowWidth, windowHeight);
        Matrix4f view = camera.getViewMatrix();

        // Update frustum
        Matrix4f projView = new Matrix4f(projection).mul(view);
        frustum.update(projView);

        // Bind shader and set uniforms
        blockShader.bind();
        blockShader.setMat4("uProjection", projection);
        blockShader.setMat4("uView", view);
        blockShader.setInt("uAtlas", 0);

        atlas.bind(0);

        // Render all visible chunk meshes
        for (var entry : world.getChunkMap().entrySet()) {
            ChunkPos pos = entry.getKey();
            Chunk chunk = entry.getValue();

            if (!frustum.isChunkVisible(pos.x(), pos.z())) continue;

            var mesh = chunk.getMesh();
            if (mesh != null && !mesh.isEmpty()) {
                mesh.draw();
            }
        }

        blockShader.unbind();
    }

    public TextureAtlas getAtlas() { return atlas; }

    public void cleanup() {
        if (blockShader != null) blockShader.cleanup();
        if (atlas != null) atlas.cleanup();
    }
}
