package com.voxelgame.world.lod;

/**
 * Level of Detail tiers for chunk rendering.
 * Higher LOD numbers = lower detail = farther from player.
 */
public enum LODLevel {
    /** Full detail — NaiveMesher with AO, per-vertex lighting. */
    LOD_0(0),
    /** Simplified faces — no AO, flat lighting, merged same-type blocks. */
    LOD_1(1),
    /** Heightmap columns — single quad per column top + side faces at height changes. */
    LOD_2(2),
    /** Flat colored quad — average surface color, single quad per chunk. */
    LOD_3(3);

    private final int level;

    LODLevel(int level) {
        this.level = level;
    }

    public int level() { return level; }

    public static LODLevel fromLevel(int level) {
        return switch (level) {
            case 0 -> LOD_0;
            case 1 -> LOD_1;
            case 2 -> LOD_2;
            case 3 -> LOD_3;
            default -> LOD_3;
        };
    }
}
