package com.npcwars.kit;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.combat.DamageCalculator;
import com.npcwars.npc.Npc;
import com.npcwars.npc.NpcSlot;
import java.util.Collection;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import org.bukkit.inventory.ItemStack;

/** Turns a kit's item list into an NPC loadout and applies it. */
public final class KitApplier {

    private final NpcWarsPlugin plugin;

    public KitApplier(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Lays a kit out like a player would: armor in its armor slot (first piece wins), a shield in the off hand, the melee
     * weapon with the highest damage per second in hotbar slot 0, and everything else (other weapons, food, pearls,
     * charges, ammunition, potions, blocks...) in the remaining hotbar slots and then the storage slots. Stack sizes are
     * kept, so a kit with 64 arrows gives 64 arrows.
     *
     * @return inventory slot (0-40) to item
     */
    public Map<Integer, ItemStack> toInventory(List<ItemStack> items) {
        Map<Integer, ItemStack> layout = new TreeMap<>();
        List<ItemStack> rest = new ArrayList<>();
        ItemStack bestWeapon = null;
        double bestScore = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            NpcSlot slot = NpcSlot.preferredFor(item);
            if (slot != NpcSlot.MAIN_HAND) {
                if (layout.putIfAbsent(slot.inventoryIndex(), item.clone()) != null) {
                    rest.add(item.clone());
                }
                continue;
            }
            double damage = plugin.attacks().weaponDamage(item);
            if (damage > DamageCalculator.BASE_ATTACK_DAMAGE) {
                double score = damage * plugin.attacks().attackSpeed(item);
                if (score > bestScore) {
                    if (bestWeapon != null) {
                        rest.add(bestWeapon);
                    }
                    bestScore = score;
                    bestWeapon = item.clone();
                    continue;
                }
            }
            rest.add(item.clone());
        }
        if (bestWeapon != null) {
            layout.put(NpcSlot.MAIN_HAND.inventoryIndex(), bestWeapon);
        }
        int next = 0;
        for (ItemStack item : rest) {
            while (layout.containsKey(next) && next < 36) {
                next++;
            }
            if (next >= 36) {
                break;
            }
            layout.put(next++, item);
        }
        return layout;
    }

    /**
     * Applies the items to every NPC.
     *
     * @return how many NPCs were changed
     */
    public int apply(Collection<Npc> targets, List<ItemStack> items) {
        Map<Integer, ItemStack> loadout = toInventory(items);
        boolean clear = plugin.settings().clearBeforeApply;
        int changed = 0;
        for (Npc npc : targets) {
            plugin.npcs().setLoadout(npc, loadout, clear);
            changed++;
        }
        return changed;
    }

}
