package com.npcwars.combat.brain;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.npc.Npc;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Shield rules shared by NPCs and real players. */
public final class Shields {

    /** Ticks a shield stays disabled after an axe hit (5 seconds, as in the game). */
    public static final int DISABLED_TICKS = 100;

    private Shields() {
    }

    /** @return {@code true} if the entity has a shield up and ready to block right now */
    public static boolean isBlocking(NpcWarsPlugin plugin, LivingEntity entity) {
        Npc npc = plugin.npcs().byEntity(entity);
        if (npc != null) {
            CombatBrain brain = plugin.fights().brainOf(npc);
            return brain != null && brain.isGuardUp(plugin.currentTick());
        }
        return entity instanceof Player player && player.isBlocking();
    }

    /** Knocks the shield out for a while (an axe hit on a raised shield). */
    public static void disable(NpcWarsPlugin plugin, LivingEntity entity, int ticks) {
        Npc npc = plugin.npcs().byEntity(entity);
        if (npc != null) {
            CombatBrain brain = plugin.fights().brainOf(npc);
            if (brain != null) {
                brain.disableShield(plugin.currentTick(), ticks);
            }
        }
        if (entity instanceof Player player) {
            player.setCooldown(Material.SHIELD, ticks);
            player.clearActiveItem();
        }
        entity.getWorld().playSound(entity.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f);
    }
}
