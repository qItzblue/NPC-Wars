package com.npcwars.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class LoadoutTest {

    private static Material[] inventory(Object... entries) {
        Material[] contents = new Material[41];
        for (int i = 0; i < entries.length; i += 2) {
            contents[(Integer) entries[i]] = (Material) entries[i + 1];
        }
        return contents;
    }

    @Test
    void findsTheFirstSlotOfEachKind() {
        Loadout loadout = Loadout.scanTypes(inventory(0, Material.NETHERITE_SWORD, 1, Material.MACE, 2, Material.WIND_CHARGE,
                3, Material.ENDER_PEARL, 4, Material.NETHERITE_AXE, 5, Material.GOLDEN_APPLE, 6, Material.BOW,
                7, Material.ARROW, 8, Material.COBWEB, 9, Material.WIND_CHARGE, 40, Material.SHIELD));
        assertEquals(0, loadout.sword);
        assertEquals(1, loadout.mace);
        assertEquals(2, loadout.windCharge, "the first stack wins");
        assertEquals(3, loadout.pearl);
        assertEquals(4, loadout.axe);
        assertEquals(5, loadout.goldenApple);
        assertEquals(6, loadout.bow);
        assertEquals(7, loadout.arrows);
        assertEquals(8, loadout.cobweb);
        assertEquals(40, loadout.shield);
        assertFalse(Loadout.has(loadout.trident));
        assertFalse(Loadout.has(loadout.totem));
    }

    @Test
    void wornArmorIsNotATool() {
        Material[] contents = inventory(39, Material.NETHERITE_HELMET, 36, Material.IRON_BOOTS);
        Loadout loadout = Loadout.scanTypes(contents);
        assertFalse(Loadout.has(loadout.sword));
        // armor slots are skipped even if (strangely) holding a tool
        contents[38] = Material.MACE;
        assertFalse(Loadout.has(Loadout.scanTypes(contents).mace));
    }

    @Test
    void emptyAndNullSlotsAreIgnoredAndPotionsAreCollected() {
        Material[] contents = inventory(2, Material.SPLASH_POTION, 4, Material.POTION, 5, Material.LINGERING_POTION);
        contents[0] = Material.AIR;
        Loadout loadout = Loadout.scanTypes(contents);
        assertEquals(java.util.List.of(2, 4, 5), loadout.potions);
        assertFalse(Loadout.has(loadout.sword));
    }

    @Test
    void roleChecksMatchTheRightMaterials() {
        assertTrue(ItemRoles.isSword(Material.WOODEN_SWORD));
        assertTrue(ItemRoles.isAxe(Material.DIAMOND_AXE));
        assertFalse(ItemRoles.isAxe(Material.DIAMOND_PICKAXE), "a pickaxe is not an axe");
        assertTrue(ItemRoles.isMelee(Material.MACE));
        assertTrue(ItemRoles.isMelee(Material.TRIDENT));
        assertFalse(ItemRoles.isMelee(Material.BOW));
        assertTrue(ItemRoles.isRanged(Material.CROSSBOW));
        assertTrue(ItemRoles.isArrow(Material.TIPPED_ARROW));
        assertTrue(ItemRoles.isThrowablePotion(Material.SPLASH_POTION));
        assertFalse(ItemRoles.isThrowablePotion(Material.POTION));
        assertTrue(ItemRoles.isGoldenApple(Material.ENCHANTED_GOLDEN_APPLE));
        assertTrue(ItemRoles.isTotem(Material.TOTEM_OF_UNDYING));
    }

    @Test
    void equipmentSlotsMapToPlayerInventoryIndexes() {
        assertEquals(39, com.npcwars.npc.NpcSlot.HEAD.inventoryIndex());
        assertEquals(38, com.npcwars.npc.NpcSlot.CHEST.inventoryIndex());
        assertEquals(37, com.npcwars.npc.NpcSlot.LEGS.inventoryIndex());
        assertEquals(36, com.npcwars.npc.NpcSlot.FEET.inventoryIndex());
        assertEquals(40, com.npcwars.npc.NpcSlot.OFF_HAND.inventoryIndex());
        assertEquals(0, com.npcwars.npc.NpcSlot.MAIN_HAND.inventoryIndex());
        assertEquals(41, com.npcwars.npc.Npc.INVENTORY_SIZE);
    }
}
