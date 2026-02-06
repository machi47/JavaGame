package com.voxelgame.ui;

import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.lwjgl.opengl.GL33.*;

/**
 * Captures the current OpenGL framebuffer to a PNG file.
 * Uses glReadPixels to bypass macOS screen capture limitations.
 */
public class Screenshot {

    private static final String SCREENSHOT_DIR = System.getProperty("user.home") + "/.voxelgame/screenshots";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /**
     * Capture the current framebuffer and save to PNG.
     *
     * @param width   viewport width
     * @param height  viewport height
     * @return the file path of the saved screenshot, or null on failure
     */
    public static String capture(int width, int height) {
        // Ensure directory exists
        File dir = new File(SCREENSHOT_DIR);
        dir.mkdirs();

        String filename = "screenshot_" + LocalDateTime.now().format(FMT) + ".png";
        File file = new File(dir, filename);

        ByteBuffer buffer = MemoryUtil.memAlloc(width * height * 4);
        try {
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // OpenGL reads bottom-to-top, flip vertically
                    int srcIdx = ((height - 1 - y) * width + x) * 4;
                    int r = buffer.get(srcIdx) & 0xFF;
                    int g = buffer.get(srcIdx + 1) & 0xFF;
                    int b = buffer.get(srcIdx + 2) & 0xFF;
                    int a = buffer.get(srcIdx + 3) & 0xFF;
                    image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }

            ImageIO.write(image, "PNG", file);
            System.out.println("[Screenshot] Saved: " + file.getAbsolutePath());
            return file.getAbsolutePath();
        } catch (IOException e) {
            System.err.println("[Screenshot] Failed: " + e.getMessage());
            return null;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }
    
    /**
     * Capture the current framebuffer and save to a specific file path.
     *
     * @param width    viewport width
     * @param height   viewport height
     * @param filePath absolute path to save the PNG
     * @return the file path of the saved screenshot, or null on failure
     */
    public static String captureToFile(int width, int height, String filePath) {
        File file = new File(filePath);
        file.getParentFile().mkdirs();

        ByteBuffer buffer = MemoryUtil.memAlloc(width * height * 4);
        try {
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int srcIdx = ((height - 1 - y) * width + x) * 4;
                    int r = buffer.get(srcIdx) & 0xFF;
                    int g = buffer.get(srcIdx + 1) & 0xFF;
                    int b = buffer.get(srcIdx + 2) & 0xFF;
                    int a = buffer.get(srcIdx + 3) & 0xFF;
                    image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }

            ImageIO.write(image, "PNG", file);
            return file.getAbsolutePath();
        } catch (IOException e) {
            System.err.println("[Screenshot] Failed to save " + filePath + ": " + e.getMessage());
            return null;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }
}
