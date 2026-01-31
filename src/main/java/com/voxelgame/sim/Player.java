package com.voxelgame.sim;

import com.voxelgame.render.Camera;
import org.joml.Vector3f;

/**
 * Player entity. Holds position, velocity, and the camera.
 */
public class Player {

    private final Camera camera;
    private final Vector3f velocity = new Vector3f();
    private boolean flyMode = true;  // start in fly mode
    private int selectedBlock = 1;   // stone by default

    public Player() {
        this.camera = new Camera();
        camera.updateVectors();
    }

    public Camera getCamera() { return camera; }
    public Vector3f getPosition() { return camera.getPosition(); }
    public Vector3f getVelocity() { return velocity; }

    public boolean isFlyMode() { return flyMode; }
    public void setFlyMode(boolean fly) { this.flyMode = fly; }
    public void toggleFlyMode() { this.flyMode = !this.flyMode; }

    public int getSelectedBlock() { return selectedBlock; }
    public void setSelectedBlock(int block) { this.selectedBlock = block; }
}
