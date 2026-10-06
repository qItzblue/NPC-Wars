package com.npcwars.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Where the useful things are in an NPC's inventory: the first slot (0-40) holding each kind of item. Scanning is cheap
 * (41 slots), and the brain rescans after every use so a spent stack is never planned with.
 */
public final class Loadout {

    public static final int NONE = -1;

    public int sword = NONE;
    public int axe = NONE;
    public int mace = NONE;
    public int spear = NONE;
    public int trident = NONE;
    public int bow = NONE;
    public int crossbow = NONE;
    public int shield = NONE;
    public int totem = NONE;
    public int arrows = NONE;
    public int windCharge = NONE;
    public int pearl = NONE;
    public int goldenApple = NONE;
    public int snowball = NONE;
    public int fireCharge = NONE;
    public int cobweb = NONE;
    public int waterBucket = NONE;
    public int lavaBucket = NONE;
    public int tnt = NONE;
    public int crystal = NONE;
    /** Food that is not a golden apple. */
    public final List<Integer> food = new ArrayList<>();
    /** Every potion, splash or lingering bottle; the brain reads the effects when it needs them. */
    public final List<Integer> potions = new ArrayList<>();

    /** @param contents the 41 slots of a player inventory (hotbar, storage, armor, off hand); entries may be {@code null} */
    public static Loadout scan(ItemStack[] contents) {
        Material[] types = new Material[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            types[slot] = item == null || item.getAmount() <= 0 ? null : item.getType();
        }
        return scanTypes(types);
    }

    /** Same as {@link #scan(ItemStack[])} for plain item types ({@code null} or air = empty slot). */
    public static Loadout scanTypes(Material[] types) {
        Loadout loadout = new Loadout();
        for (int slot = 0; slot < types.length; slot++) {
            Material type = types[slot];
            if (type == null || type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
                continue;
            }
            // Armor slots (36-39) hold worn pieces, not tools.
            if (slot >= 36 && slot <= 39) {
                continue;
            }
            loadout.classify(slot, type);
        }
        return loadout;
    }

    private void classify(int slot, Material type) {
        sword = first(sword, slot, ItemRoles.isSword(type));
        axe = first(axe, slot, ItemRoles.isAxe(type));
        mace = first(mace, slot, ItemRoles.isMace(type));
        spear = first(spear, slot, ItemRoles.isSpear(type));
        trident = first(trident, slot, ItemRoles.isTrident(type));
        bow = first(bow, slot, ItemRoles.isBow(type));
        crossbow = first(crossbow, slot, ItemRoles.isCrossbow(type));
        shield = first(shield, slot, ItemRoles.isShield(type));
        totem = first(totem, slot, ItemRoles.isTotem(type));
        arrows = first(arrows, slot, ItemRoles.isArrow(type));
        windCharge = first(windCharge, slot, ItemRoles.isWindCharge(type));
        pearl = first(pearl, slot, ItemRoles.isPearl(type));
        goldenApple = first(goldenApple, slot, ItemRoles.isGoldenApple(type));
        snowball = first(snowball, slot, ItemRoles.isSnowball(type));
        fireCharge = first(fireCharge, slot, ItemRoles.isFireCharge(type));
        cobweb = first(cobweb, slot, ItemRoles.isCobweb(type));
        waterBucket = first(waterBucket, slot, ItemRoles.isWaterBucket(type));
        lavaBucket = first(lavaBucket, slot, ItemRoles.isLavaBucket(type));
        tnt = first(tnt, slot, ItemRoles.isTnt(type));
        crystal = first(crystal, slot, ItemRoles.isCrystal(type));
        if (ItemRoles.isPotion(type)) {
            potions.add(slot);
        } else if (!ItemRoles.isGoldenApple(type) && edible(type)) {
            food.add(slot);
        }
    }

    private static boolean edible(Material type) {
        try {
            return type.isEdible();
        } catch (RuntimeException | LinkageError ex) {
            return false; // item registry not available (unit tests)
        }
    }

    private static int first(int current, int slot, boolean matches) {
        return current == NONE && matches ? slot : current;
    }

    public static boolean has(int slot) {
        return slot != NONE;
    }

    /** @return the first slot whose item satisfies the test, or {@link #NONE} */
    public static int find(ItemStack[] contents, Predicate<ItemStack> test) {
        for (int slot = 0; slot < contents.length; slot++) {
            if (slot >= 36 && slot <= 39) {
                continue;
            }
            ItemStack item = contents[slot];
            if (item != null && !item.getType().isAir() && test.test(item)) {
                return slot;
            }
        }
        return NONE;
    }
}
