package com.voxelgame.platform;

/** GLFW window creation and management. */
public class Window {

    private long handle;
    private int width;
    private int height;

    public Window(int width, int height, String title) {
        this.width = width;
        this.height = height;
        // TODO: GLFW init, window creation
    }

    public void pollEvents() { /* TODO: glfwPollEvents */ }

    public void swapBuffers() { /* TODO: glfwSwapBuffers */ }

    public boolean shouldClose() { return false; /* TODO: glfwWindowShouldClose */ }

    public void destroy() { /* TODO: cleanup */ }

    public long getHandle() { return handle; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
