package com.voxelgame.sim;

import com.voxelgame.world.Blocks;

/**
 * Armor item registry and mechanics.
 * Alpha armor: Leather, Iron, Diamond, Gold (4 materials × 4 pieces = 16 armor items).
 */
public final class ArmorItem {
    private ArmorItem() {}

    public enum Slot {
        HELMET(0),
        CHESTPLATE(1),
        LEGGINGS(2),
        BOOTS(3);

        public final int index;
        Slot(int index) { this.index = index; }
    }

    public enum Material {
        LEATHER(1.5f, 55),      // Weakest, 55 durability
        GOLD(2.0f, 77),         // Weak defense but fast enchanting
        IRON(3.0f, 165),        // Standard
        DIAMOND(4.0f, 363);     // Best

        public final float defenseMultiplier;
        public final int durabilityBase;

        Material(float def, int dur) {
            this.defenseMultiplier = def;
            this.durabilityBase = dur;
        }
    }

    private static class ArmorPiece {
        final int blockId;
        final Slot slot;
        final Material material;
        final String displayName;

        ArmorPiece(int blockId, Slot slot, Material material, String displayName) {
            this.blockId = blockId;
            this.slot = slot;
            this.material = material;
            this.displayName = displayName;
        }

        float getDefense() {
            // Defense points per piece (helmet=2, chest=3, legs=2.5, boots=1.5)
            return switch (slot) {
                case HELMET -> 2.0f * material.defenseMultiplier;
                case CHESTPLATE -> 3.0f * material.defenseMultiplier;
                case LEGGINGS -> 2.5f * material.defenseMultiplier;
                case BOOTS -> 1.5f * material.defenseMultiplier;
            };
        }

        int getMaxDurability() {
            // Durability multiplier per slot
            return switch (slot) {
                case HELMET -> (int)(material.durabilityBase * 0.68f);
                case CHESTPLATE -> material.durabilityBase;
                case LEGGINGS -> (int)(material.durabilityBase * 0.94f);
                case BOOTS -> (int)(material.durabilityBase * 0.77f);
            };
        }
    }

    private static final ArmorPiece[] REGISTRY = new ArmorPiece[] {
        // Leather armor
        new ArmorPiece(Blocks.LEATHER_HELMET.id(),     Slot.HELMET,     Material.LEATHER, "Leather Helmet"),
        new ArmorPiece(Blocks.LEATHER_CHESTPLATE.id(), Slot.CHESTPLATE, Material.LEATHER, "Leather Chestplate"),
        new ArmorPiece(Blocks.LEATHER_LEGGINGS.id(),   Slot.LEGGINGS,   Material.LEATHER, "Leather Leggings"),
        new ArmorPiece(Blocks.LEATHER_BOOTS.id(),      Slot.BOOTS,      Material.LEATHER, "Leather Boots"),

        // Iron armor
        new ArmorPiece(Blocks.IRON_HELMET.id(),        Slot.HELMET,     Material.IRON, "Iron Helmet"),
        new ArmorPiece(Blocks.IRON_CHESTPLATE.id(),    Slot.CHESTPLATE, Material.IRON, "Iron Chestplate"),
        new ArmorPiece(Blocks.IRON_LEGGINGS.id(),      Slot.LEGGINGS,   Material.IRON, "Iron Leggings"),
        new ArmorPiece(Blocks.IRON_BOOTS.id(),         Slot.BOOTS,      Material.IRON, "Iron Boots"),

        // Diamond armor
        new ArmorPiece(Blocks.DIAMOND_HELMET.id(),     Slot.HELMET,     Material.DIAMOND, "Diamond Helmet"),
        new ArmorPiece(Blocks.DIAMOND_CHESTPLATE.id(), Slot.CHESTPLATE, Material.DIAMOND, "Diamond Chestplate"),
        new ArmorPiece(Blocks.DIAMOND_LEGGINGS.id(),   Slot.LEGGINGS,   Material.DIAMOND, "Diamond Leggings"),
        new ArmorPiece(Blocks.DIAMOND_BOOTS.id(),      Slot.BOOTS,      Material.DIAMOND, "Diamond Boots"),

        // Gold armor
        new ArmorPiece(Blocks.GOLD_HELMET.id(),        Slot.HELMET,     Material.GOLD, "Gold Helmet"),
        new ArmorPiece(Blocks.GOLD_CHESTPLATE.id(),    Slot.CHESTPLATE, Material.GOLD, "Gold Chestplate"),
        new ArmorPiece(Blocks.GOLD_LEGGINGS.id(),      Slot.LEGGINGS,   Material.GOLD, "Gold Leggings"),
        new ArmorPiece(Blocks.GOLD_BOOTS.id(),         Slot.BOOTS,      Material.GOLD, "Gold Boots"),
    };

    private static ArmorPiece getArmorPiece(int blockId) {
        for (ArmorPiece piece : REGISTRY) {
            if (piece.blockId == blockId) return piece;
        }
        return null;
    }

    /** Check if a block ID is armor. */
    public static boolean isArmor(int blockId) {
        return getArmorPiece(blockId) != null;
    }

    /** Get defense points for an armor piece. */
    public static float getDefense(int blockId) {
        ArmorPiece piece = getArmorPiece(blockId);
        return piece != null ? piece.getDefense() : 0.0f;
    }

    /** Get max durability for an armor piece. */
    public static int getMaxDurability(int blockId) {
        ArmorPiece piece = getArmorPiece(blockId);
        return piece != null ? piece.getMaxDurability() : 0;
    }

    /** Get armor slot for a piece. */
    public static Slot getSlot(int blockId) {
        ArmorPiece piece = getArmorPiece(blockId);
        return piece != null ? piece.slot : null;
    }

    /** Get display name. */
    public static String getDisplayName(int blockId) {
        ArmorPiece piece = getArmorPiece(blockId);
        return piece != null ? piece.displayName : "";
    }
}
