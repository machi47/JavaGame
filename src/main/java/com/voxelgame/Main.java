package com.voxelgame;

import com.voxelgame.core.GameLoop;

/** Entry point for VoxelGame. */
public class Main {
    public static void main(String[] args) {
        System.out.println("VoxelGame starting...");
        new GameLoop().run();
    }
}
