package com.npcwars.combat;

import org.bukkit.Material;

/**
 * What an item is good for in a fight, decided from its type alone (so it can be tested without a server). The combat
 * brain uses these to find a mace, a wind charge, a pearl and so on in an NPC's inventory.
 */
public final class ItemRoles {

    private ItemRoles() {
    }

    private static String name(Material type) {
        return type == null ? "" : type.name();
    }

    public static boolean isSword(Material type) {
        return name(type).endsWith("_SWORD");
    }

    public static boolean isAxe(Material type) {
        return name(type).endsWith("_AXE");
    }

    public static boolean isSpear(Material type) {
        return name(type).endsWith("_SPEAR");
    }

    public static boolean isMace(Material type) {
        return type == Material.MACE;
    }

    public static boolean isTrident(Material type) {
        return type == Material.TRIDENT;
    }

    public static boolean isBow(Material type) {
        return type == Material.BOW;
    }

    public static boolean isCrossbow(Material type) {
        return type == Material.CROSSBOW;
    }

    public static boolean isShield(Material type) {
        return type == Material.SHIELD;
    }

    public static boolean isTotem(Material type) {
        return type == Material.TOTEM_OF_UNDYING;
    }

    public static boolean isArrow(Material type) {
        return type == Material.ARROW || type == Material.SPECTRAL_ARROW || type == Material.TIPPED_ARROW;
    }

    public static boolean isWindCharge(Material type) {
        return type == Material.WIND_CHARGE;
    }

    public static boolean isPearl(Material type) {
        return type == Material.ENDER_PEARL;
    }

    public static boolean isGoldenApple(Material type) {
        return type == Material.GOLDEN_APPLE || type == Material.ENCHANTED_GOLDEN_APPLE;
    }

    public static boolean isPotion(Material type) {
        return type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION;
    }

    public static boolean isThrowablePotion(Material type) {
        return type == Material.SPLASH_POTION || type == Material.LINGERING_POTION;
    }

    public static boolean isSnowball(Material type) {
        return type == Material.SNOWBALL || type == Material.EGG;
    }

    public static boolean isFireCharge(Material type) {
        return type == Material.FIRE_CHARGE;
    }

    public static boolean isCobweb(Material type) {
        return type == Material.COBWEB;
    }

    public static boolean isWaterBucket(Material type) {
        return type == Material.WATER_BUCKET;
    }

    public static boolean isLavaBucket(Material type) {
        return type == Material.LAVA_BUCKET;
    }

    public static boolean isTnt(Material type) {
        return type == Material.TNT;
    }

    public static boolean isCrystal(Material type) {
        return type == Material.END_CRYSTAL;
    }

    /** @return {@code true} for anything that can be wielded to hit with (swords, axes, mace, spears, trident) */
    public static boolean isMelee(Material type) {
        return isSword(type) || isAxe(type) || isMace(type) || isSpear(type) || isTrident(type);
    }

    /** @return {@code true} for a ranged weapon that needs ammunition or is thrown (bow, crossbow, trident) */
    public static boolean isRanged(Material type) {
        return isBow(type) || isCrossbow(type) || isTrident(type);
    }
}
