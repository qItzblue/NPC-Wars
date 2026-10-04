package com.npcwars.combat;

import com.npcwars.npc.Npc;
import org.bukkit.entity.LivingEntity;

/** Per-NPC combat state while a fight is running. */
final class Fighter {

    final Npc npc;
    LivingEntity target;
    long nextAttackTick;
    long nextSearchTick;

    Fighter(Npc npc, long now, int retargetInterval) {
        this.npc = npc;
        // Spread the target searches of a crowd over the whole interval and add a short wind-up before the first hit.
        this.nextSearchTick = now + Math.floorMod(npc.id(), Math.max(1, retargetInterval));
        this.nextAttackTick = now + 10;
    }
}
