package com.voxelgame.world;

/**
 * Block registry. Defines all block types and provides lookup by ID.
 * MVP set: 15 blocks (AIR through BEDROCK).
 *
 * Texture indices refer to positions in the texture atlas (row-major).
 * Atlas layout TBD — indices are placeholders until atlas.png is created.
 */
public final class Blocks {

    private Blocks() {}

    // --- Block definitions (id, name, solid, transparent, textures) ---

    public static final Block AIR          = new Block( 0, "air",          false, true,  new int[]{0});
    public static final Block STONE        = new Block( 1, "stone",        true,  false, new int[]{1});
    public static final Block COBBLESTONE  = new Block( 2, "cobblestone",  true,  false, new int[]{2});
    public static final Block DIRT         = new Block( 3, "dirt",         true,  false, new int[]{3});
    public static final Block GRASS        = new Block( 4, "grass",        true,  false, new int[]{4, 3, 5, 5, 5, 5}); // top=grass_top, bottom=dirt, sides=grass_side
    public static final Block SAND         = new Block( 5, "sand",         true,  false, new int[]{6});
    public static final Block GRAVEL       = new Block( 6, "gravel",       true,  false, new int[]{7});
    public static final Block LOG          = new Block( 7, "log",          true,  false, new int[]{8, 8, 9, 9, 9, 9}); // top/bottom=log_end, sides=log_bark
    public static final Block LEAVES       = new Block( 8, "leaves",       true,  true,  new int[]{10});
    public static final Block WATER        = new Block( 9, "water",        false, true,  new int[]{11});
    public static final Block COAL_ORE     = new Block(10, "coal_ore",     true,  false, new int[]{12});
    public static final Block IRON_ORE     = new Block(11, "iron_ore",     true,  false, new int[]{13});
    public static final Block GOLD_ORE     = new Block(12, "gold_ore",     true,  false, new int[]{14});
    public static final Block DIAMOND_ORE  = new Block(13, "diamond_ore",  true,  false, new int[]{15});
    public static final Block BEDROCK      = new Block(14, "bedrock",      true,  false, new int[]{16});

    /** All blocks indexed by ID for fast lookup. */
    private static final Block[] REGISTRY = {
        AIR, STONE, COBBLESTONE, DIRT, GRASS, SAND, GRAVEL,
        LOG, LEAVES, WATER, COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE, BEDROCK
    };

    /**
     * Returns the block for the given ID.
     * @param id block ID (0–14)
     * @return the Block, or AIR if out of range
     */
    public static Block get(int id) {
        if (id < 0 || id >= REGISTRY.length) return AIR;
        return REGISTRY[id];
    }

    /** Total number of registered block types. */
    public static int count() {
        return REGISTRY.length;
    }
}
