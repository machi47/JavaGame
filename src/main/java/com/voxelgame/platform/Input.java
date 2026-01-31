package com.voxelgame.platform;

/** Keyboard and mouse input handling. */
public class Input {

    private static final boolean[] keys = new boolean[512];
    private static double mouseX, mouseY;
    private static double mouseDX, mouseDY;

    public static void init(long windowHandle) {
        // TODO: register GLFW key/mouse callbacks
    }

    public static boolean isKeyDown(int key) { return keys[key]; }

    public static double getMouseDX() { return mouseDX; }
    public static double getMouseDY() { return mouseDY; }

    public static void endFrame() {
        mouseDX = 0;
        mouseDY = 0;
    }
}
