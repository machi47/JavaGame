package com.voxelgame.world.mesh;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * GPU-side mesh data for a chunk. Holds VAO/VBO/EBO handles,
 * vertex count, and manages upload/disposal of vertex data.
 */
public class ChunkMesh {

    private int vao;
    private int vbo;
    private int ebo;
    private int indexCount;
    private boolean uploaded = false;

    /**
     * Upload mesh data to GPU.
     * Vertex format: [x, y, z, u, v, light] per vertex.
     * Quads are turned into triangles via index buffer.
     */
    public void upload(float[] vertices, int quadCount) {
        if (quadCount == 0) {
            indexCount = 0;
            return;
        }

        if (!uploaded) {
            vao = glGenVertexArrays();
            vbo = glGenBuffers();
            ebo = glGenBuffers();
        }

        glBindVertexArray(vao);

        // Upload vertex data
        FloatBuffer vertBuf = MemoryUtil.memAllocFloat(vertices.length);
        vertBuf.put(vertices).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertBuf, GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(vertBuf);

        // Generate indices for quads -> triangles
        indexCount = quadCount * 6;
        IntBuffer idxBuf = MemoryUtil.memAllocInt(indexCount);
        for (int i = 0; i < quadCount; i++) {
            int base = i * 4;
            idxBuf.put(base).put(base + 1).put(base + 2);
            idxBuf.put(base).put(base + 2).put(base + 3);
        }
        idxBuf.flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idxBuf, GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(idxBuf);

        int stride = 6 * Float.BYTES; // x, y, z, u, v, light

        // Position (location 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        // TexCoord (location 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        // Light (location 2)
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);
        uploaded = true;
    }

    public void draw() {
        if (!uploaded || indexCount == 0) return;
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void dispose() {
        if (uploaded) {
            glDeleteVertexArrays(vao);
            glDeleteBuffers(vbo);
            glDeleteBuffers(ebo);
            uploaded = false;
            indexCount = 0;
        }
    }

    public boolean isEmpty() { return indexCount == 0; }
    public boolean isUploaded() { return uploaded; }
}
