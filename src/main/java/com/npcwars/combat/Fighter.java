package com.npcwars.combat;

import com.npcwars.combat.brain.CombatBrain;
import com.npcwars.npc.Npc;
import org.bukkit.entity.LivingEntity;

/** Per-NPC combat state while a fight is running: who it fights, and the brain that decides what to do about it. */
final class Fighter {

    final Npc npc;
    final CombatBrain brain;
    LivingEntity target;
    long nextSearchTick;

    Fighter(Npc npc, long now, int retargetInterval, CombatBrain brain) {
        this.npc = npc;
        this.brain = brain;
        // Spread the target searches of a crowd over the whole interval.
        this.nextSearchTick = now + Math.floorMod(npc.id(), Math.max(1, retargetInterval));
    }
}
