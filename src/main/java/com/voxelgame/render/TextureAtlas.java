package com.voxelgame.render;

import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Texture atlas loaded from terrain.png.
 * 16x16 pixel tiles arranged in an 8×16 grid (128×256).
 * Rows 0-7: block textures (64 tiles)
 * Rows 8-15: item sprites (64 tiles)
 */
public class TextureAtlas {

    private static final int TILE_SIZE = 16;
    private static final int TILES_PER_ROW = 8;
    private static final int TILE_COUNT = 128; // 8×16 grid (expanded for items)
    private static final int ATLAS_WIDTH = TILES_PER_ROW * TILE_SIZE;  // 128
    private static final int ATLAS_HEIGHT = ((TILE_COUNT + TILES_PER_ROW - 1) / TILES_PER_ROW) * TILE_SIZE; // 256

    private int textureId;

    public void init() {
        ByteBuffer pixels = loadAtlasFromPNG();

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        // Use GL_SRGB_ALPHA for proper color management:
        // - PNG textures are stored in sRGB color space (gamma ~2.2)
        // - GL_SRGB_ALPHA tells OpenGL to convert to linear on sample
        // - This allows correct lighting math in linear space
        // - Final gamma correction in composite shader converts back to sRGB
        glTexImage2D(GL_TEXTURE_2D, 0, GL_SRGB_ALPHA, ATLAS_WIDTH, ATLAS_HEIGHT,
            0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

        MemoryUtil.memFree(pixels);
        glBindTexture(GL_TEXTURE_2D, 0);

        System.out.println("TextureAtlas loaded from terrain.png: " + ATLAS_WIDTH + "x" + ATLAS_HEIGHT +
            " (" + TILE_COUNT + " tiles)");
    }

    /**
     * Load terrain.png from resources and convert to ByteBuffer (RGBA).
     */
    private ByteBuffer loadAtlasFromPNG() {
        try (InputStream in = getClass().getResourceAsStream("/textures/terrain.png")) {
            if (in == null) {
                throw new RuntimeException("Could not find /textures/terrain.png in resources");
            }

            BufferedImage img = ImageIO.read(in);
            
            if (img.getWidth() != ATLAS_WIDTH || img.getHeight() != ATLAS_HEIGHT) {
                throw new RuntimeException("terrain.png has wrong dimensions: " + 
                    img.getWidth() + "x" + img.getHeight() + " (expected " + 
                    ATLAS_WIDTH + "x" + ATLAS_HEIGHT + ")");
            }

            // Convert BufferedImage to ByteBuffer (RGBA format)
            ByteBuffer pixels = MemoryUtil.memAlloc(ATLAS_WIDTH * ATLAS_HEIGHT * 4);
            
            for (int y = 0; y < ATLAS_HEIGHT; y++) {
                for (int x = 0; x < ATLAS_WIDTH; x++) {
                    int rgba = img.getRGB(x, y);
                    
                    // Extract RGBA components (BufferedImage uses ARGB format)
                    int r = (rgba >> 16) & 0xFF;
                    int g = (rgba >> 8) & 0xFF;
                    int b = rgba & 0xFF;
                    int a = (rgba >> 24) & 0xFF;
                    
                    // Write to ByteBuffer in RGBA order
                    pixels.put((byte) r);
                    pixels.put((byte) g);
                    pixels.put((byte) b);
                    pixels.put((byte) a);
                }
            }
            
            pixels.flip();
            return pixels;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to load terrain.png", e);
        }
    }

    /** Get UV coordinates for a tile index: [u0, v0, u1, v1] */
    public float[] getUV(int tileIndex) {
        int col = tileIndex % TILES_PER_ROW;
        int row = tileIndex / TILES_PER_ROW;
        float u0 = (float)(col * TILE_SIZE) / ATLAS_WIDTH;
        float v0 = (float)(row * TILE_SIZE) / ATLAS_HEIGHT;
        float u1 = (float)((col + 1) * TILE_SIZE) / ATLAS_WIDTH;
        float v1 = (float)((row + 1) * TILE_SIZE) / ATLAS_HEIGHT;
        return new float[]{u0, v0, u1, v1};
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void cleanup() {
        glDeleteTextures(textureId);
    }
}
