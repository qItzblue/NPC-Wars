package com.npcwars.combat.brain;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.combat.Loadout;
import com.npcwars.config.Settings;
import com.npcwars.npc.Npc;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Everything a tactic needs to know about this tick of a fight. */
final class CombatContext {

    final NpcWarsPlugin plugin;
    final Npc npc;
    final Player body;
    final LivingEntity target;
    final long now;
    final Settings settings;
    final CombatBrain brain;
    final Loadout loadout;
    /** Distance from the NPC's eyes to the nearest point of the target's hitbox. */
    final double reach;
    /** Distance on the ground plane between the two bodies. */
    final double horizontal;
    final boolean sees;

    CombatContext(NpcWarsPlugin plugin, Npc npc, Player body, LivingEntity target, long now, CombatBrain brain,
                  double reach, double horizontal, boolean sees) {
        this.plugin = plugin;
        this.npc = npc;
        this.body = body;
        this.target = target;
        this.now = now;
        this.settings = plugin.settings();
        this.brain = brain;
        this.loadout = brain.loadout();
        this.reach = reach;
        this.horizontal = horizontal;
        this.sees = sees;
    }

    double health() {
        return body.getHealth();
    }
}
