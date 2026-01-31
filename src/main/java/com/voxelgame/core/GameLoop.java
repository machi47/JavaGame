package com.voxelgame.core;

import com.voxelgame.platform.Input;
import com.voxelgame.platform.Window;
import com.voxelgame.render.GLInit;
import com.voxelgame.sim.Controller;
import com.voxelgame.sim.Player;

import static org.lwjgl.opengl.GL33.*;

/**
 * Fixed-timestep game loop. Drives update ticks and render frames.
 */
public class GameLoop {

    private Window window;
    private Time time;
    private Player player;
    private Controller controller;

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        window = new Window(1280, 720, "VoxelGame");
        GLInit.init();
        GLInit.setViewport(window.getWidth(), window.getHeight());

        time = new Time();
        time.init();

        player = new Player();
        controller = new Controller(player);

        Input.init(window.getHandle());
        Input.lockCursor();

        System.out.println("VoxelGame initialized successfully!");
    }

    private void loop() {
        while (!window.shouldClose()) {
            time.update();
            float dt = time.getDeltaTime();

            window.pollEvents();

            if (window.wasResized()) {
                GLInit.setViewport(window.getWidth(), window.getHeight());
            }

            // Update
            controller.update(dt);

            // Render
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // End frame
            Input.endFrame();
            window.swapBuffers();
        }
    }

    private void cleanup() {
        window.destroy();
    }

    public Window getWindow() { return window; }
    public Time getTime() { return time; }
    public Player getPlayer() { return player; }
}
