package com.npcwars.npc;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** The six equipment slots an NPC can wear. */
public enum NpcSlot {
    HEAD("head", EquipmentSlot.HEAD),
    CHEST("chest", EquipmentSlot.CHEST),
    LEGS("legs", EquipmentSlot.LEGS),
    FEET("feet", EquipmentSlot.FEET),
    MAIN_HAND("main-hand", EquipmentSlot.HAND),
    OFF_HAND("off-hand", EquipmentSlot.OFF_HAND);

    private final String key;
    private final EquipmentSlot bukkit;

    NpcSlot(String key, EquipmentSlot bukkit) {
        this.key = key;
        this.bukkit = bukkit;
    }

    /** Key used in data.yml. */
    public String key() {
        return key;
    }

    public EquipmentSlot bukkit() {
        return bukkit;
    }

    public boolean isArmor() {
        return this == HEAD || this == CHEST || this == LEGS || this == FEET;
    }

    /** @return {@code true} if the item may be placed in this slot (hands accept anything, armor slots are strict) */
    public boolean accepts(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return switch (this) {
            case MAIN_HAND, OFF_HAND -> true;
            default -> item.getType().isItem() && item.getType().getEquipmentSlot() == bukkit;
        };
    }

    /** @return the slot an item belongs in when it is auto-placed (armor by type, shield to the off hand, rest main hand) */
    public static NpcSlot preferredFor(ItemStack item) {
        Material type = item.getType();
        if (type == Material.SHIELD) {
            return OFF_HAND;
        }
        if (type.isItem()) {
            switch (type.getEquipmentSlot()) {
                case HEAD:
                    return HEAD;
                case CHEST:
                    return CHEST;
                case LEGS:
                    return LEGS;
                case FEET:
                    return FEET;
                default:
                    break;
            }
        }
        return MAIN_HAND;
    }

    public static NpcSlot fromKey(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (NpcSlot slot : values()) {
            if (slot.key.equals(lower)) {
                return slot;
            }
        }
        return null;
    }
}
