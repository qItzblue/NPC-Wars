package com.npcwars.combat.brain;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Moving things between an NPC's slots and hands the way a player would: scroll the hotbar, swap, use up one. */
final class Hands {

    static final int OFF_HAND = 40;

    private Hands() {
    }

    /** Brings the item in {@code slot} into the main hand (scrolling the hotbar, or swapping from storage). */
    static void select(Player player, int slot) {
        if (slot < 0 || slot == OFF_HAND || slot > 35) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        if (slot < 9) {
            if (inventory.getHeldItemSlot() != slot) {
                inventory.setHeldItemSlot(slot);
            }
            return;
        }
        int held = inventory.getHeldItemSlot();
        ItemStack inHand = inventory.getItem(held);
        inventory.setItem(held, inventory.getItem(slot));
        inventory.setItem(slot, inHand);
    }

    static ItemStack at(Player player, int slot) {
        return slot < 0 ? null : player.getInventory().getItem(slot);
    }

    static Material typeAt(Player player, int slot) {
        ItemStack item = at(player, slot);
        return item == null ? Material.AIR : item.getType();
    }

    /** Uses up one item of the stack in {@code slot}. */
    static void consumeOne(Player player, int slot) {
        ItemStack item = at(player, slot);
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (item.getAmount() <= 1) {
            player.getInventory().setItem(slot, null);
        } else {
            item.setAmount(item.getAmount() - 1);
            player.getInventory().setItem(slot, item);
        }
    }

    /** Swaps the contents of two slots. */
    static void swap(Player player, int a, int b) {
        PlayerInventory inventory = player.getInventory();
        ItemStack first = inventory.getItem(a);
        inventory.setItem(a, inventory.getItem(b));
        inventory.setItem(b, first);
    }
}
