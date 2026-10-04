package com.npcwars.kit;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.npc.Npc;
import com.npcwars.npc.NpcSlot;
import java.util.Collection;
import java.util.EnumMap;
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
     * Chooses a slot for each item: armor goes to its armor slot (first piece wins), a shield to the off hand, and the
     * item with the highest attack damage to the main hand (a non-weapon such as a bow only if nothing better exists).
     */
    public Map<NpcSlot, ItemStack> toLoadout(List<ItemStack> items) {
        Map<NpcSlot, ItemStack> loadout = new EnumMap<>(NpcSlot.class);
        ItemStack bestHand = null;
        double bestDamage = -1;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            NpcSlot slot = NpcSlot.preferredFor(item);
            if (slot == NpcSlot.MAIN_HAND) {
                double damage = plugin.attacks().weaponDamage(item);
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestHand = item;
                }
            } else {
                loadout.putIfAbsent(slot, single(item));
            }
        }
        if (bestHand != null) {
            loadout.put(NpcSlot.MAIN_HAND, single(bestHand));
        }
        return loadout;
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
