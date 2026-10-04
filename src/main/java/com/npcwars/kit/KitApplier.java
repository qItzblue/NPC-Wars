package com.npcwars.kit;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.combat.DamageCalculator;
import com.npcwars.npc.Npc;
import com.npcwars.npc.NpcSlot;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Turns a kit's item list into an NPC loadout and applies it. */
public final class KitApplier {

    private final NpcWarsPlugin plugin;

    public KitApplier(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Chooses a slot for each item: armor goes to its armor slot (first piece wins), a shield to the off hand, and the
     * weapon with the highest damage per second to the main hand. Only if the kit has no melee weapon at all, the first
     * holdable item (a bow, a tool, ...) is used; food, blocks and ammunition never go into the hand.
     */
    public Map<NpcSlot, ItemStack> toLoadout(List<ItemStack> items) {
        Map<NpcSlot, ItemStack> loadout = new EnumMap<>(NpcSlot.class);
        ItemStack bestWeapon = null;
        double bestScore = 0;
        ItemStack fallback = null;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            NpcSlot slot = NpcSlot.preferredFor(item);
            if (slot != NpcSlot.MAIN_HAND) {
                loadout.putIfAbsent(slot, single(item));
                continue;
            }
            double damage = plugin.attacks().weaponDamage(item);
            if (damage > DamageCalculator.BASE_ATTACK_DAMAGE) {
                double score = damage * plugin.attacks().attackSpeed(item);
                if (score > bestScore) {
                    bestScore = score;
                    bestWeapon = item;
                }
            } else if (fallback == null && isHoldable(item)) {
                fallback = item;
            }
        }
        ItemStack hand = bestWeapon != null ? bestWeapon : fallback;
        if (hand != null) {
            loadout.put(NpcSlot.MAIN_HAND, single(hand));
        }
        return loadout;
    }

    private static boolean isHoldable(ItemStack item) {
        Material type = item.getType();
        return !type.isEdible() && !type.isBlock() && type != Material.ARROW
                && type != Material.SPECTRAL_ARROW && type != Material.TIPPED_ARROW;
    }

    /**
     * Applies the items to every NPC.
     *
     * @return how many NPCs were changed
     */
    public int apply(Collection<Npc> targets, List<ItemStack> items) {
        Map<NpcSlot, ItemStack> loadout = toLoadout(items);
        boolean clear = plugin.settings().clearBeforeApply;
        int changed = 0;
        for (Npc npc : targets) {
            plugin.npcs().setLoadout(npc, loadout, clear);
            changed++;
        }
        return changed;
    }

    private static ItemStack single(ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        return copy;
    }
}
