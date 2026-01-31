package com.voxelgame.sim;

import com.voxelgame.platform.Input;
import com.voxelgame.render.Camera;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Translates input events into player actions: movement, jumping,
 * camera rotation, fly mode toggle.
 */
public class Controller {

    private static final float FLY_SPEED = 20.0f;
    private static final float MOUSE_SENSITIVITY = 0.1f;

    private final Player player;

    public Controller(Player player) {
        this.player = player;
    }

    public void update(float dt) {
        handleMouseLook();
        handleMovement(dt);
        handleModeToggles();
    }

    private void handleMouseLook() {
        if (!Input.isCursorLocked()) return;

        double dx = Input.getMouseDX();
        double dy = Input.getMouseDY();

        if (dx != 0 || dy != 0) {
            player.getCamera().rotate(
                (float) dx * MOUSE_SENSITIVITY,
                (float) -dy * MOUSE_SENSITIVITY
            );
        }
    }

    private void handleMovement(float dt) {
        Camera camera = player.getCamera();
        Vector3f pos = camera.getPosition();
        Vector3f front = camera.getFront();
        Vector3f right = camera.getRight();

        float speed = FLY_SPEED;
        if (Input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) {
            speed *= 2.5f;
        }

        float moveX = 0, moveY = 0, moveZ = 0;

        if (player.isFlyMode()) {
            // In fly mode, move along camera direction
            if (Input.isKeyDown(GLFW_KEY_W)) {
                moveX += front.x;
                moveY += front.y;
                moveZ += front.z;
            }
            if (Input.isKeyDown(GLFW_KEY_S)) {
                moveX -= front.x;
                moveY -= front.y;
                moveZ -= front.z;
            }
            if (Input.isKeyDown(GLFW_KEY_A)) {
                moveX -= right.x;
                moveY -= right.y;
                moveZ -= right.z;
            }
            if (Input.isKeyDown(GLFW_KEY_D)) {
                moveX += right.x;
                moveY += right.y;
                moveZ += right.z;
            }
            if (Input.isKeyDown(GLFW_KEY_SPACE)) {
                moveY += 1.0f;
            }
            if (Input.isKeyDown(GLFW_KEY_LEFT_CONTROL)) {
                moveY -= 1.0f;
            }
        } else {
            // Non-fly: move on XZ plane
            Vector3f flatFront = new Vector3f(front.x, 0, front.z).normalize();
            Vector3f flatRight = new Vector3f(right.x, 0, right.z).normalize();

            if (Input.isKeyDown(GLFW_KEY_W)) {
                moveX += flatFront.x;
                moveZ += flatFront.z;
            }
            if (Input.isKeyDown(GLFW_KEY_S)) {
                moveX -= flatFront.x;
                moveZ -= flatFront.z;
            }
            if (Input.isKeyDown(GLFW_KEY_A)) {
                moveX -= flatRight.x;
                moveZ -= flatRight.z;
            }
            if (Input.isKeyDown(GLFW_KEY_D)) {
                moveX += flatRight.x;
                moveZ += flatRight.z;
            }
            if (Input.isKeyDown(GLFW_KEY_SPACE)) {
                moveY += 1.0f;
            }
        }

        // Normalize horizontal movement
        float len = (float) Math.sqrt(moveX * moveX + moveY * moveY + moveZ * moveZ);
        if (len > 0.001f) {
            float inv = 1.0f / len;
            pos.x += moveX * inv * speed * dt;
            pos.y += moveY * inv * speed * dt;
            pos.z += moveZ * inv * speed * dt;
        }
    }

    private void handleModeToggles() {
        if (Input.isKeyPressed(GLFW_KEY_F3)) {
            player.toggleFlyMode();
            System.out.println("Fly mode: " + (player.isFlyMode() ? "ON" : "OFF"));
        }

        // ESC to toggle cursor lock
        if (Input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            if (Input.isCursorLocked()) {
                Input.unlockCursor();
            } else {
                Input.lockCursor();
            }
        }
    }
}
