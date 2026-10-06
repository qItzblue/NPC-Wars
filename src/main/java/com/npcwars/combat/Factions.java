package com.npcwars.combat;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.npc.Npc;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Maps combatants to "sides". A team member's side is the team number (positive). An NPC without a team is a side of
 * its own (the negative of its id), so unteamed NPCs fight everyone else. A player without a team is side 0, which is
 * never allied with anything.
 */
public final class Factions {

    /** Returned for entities that take no part in team logic (animals, monsters, ...). */
    public static final int NONE = Integer.MIN_VALUE;
    /** The side shared by all players who are not on a team. */
    public static final int UNTEAMED_PLAYERS = 0;

    private final NpcWarsPlugin plugin;

    public Factions(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    public int of(Npc npc) {
        int team = plugin.teams().teamOfNpc(npc.id());
        return team > 0 ? team : -npc.id();
    }

    public int of(Player player) {
        return plugin.teams().teamOfPlayer(player.getUniqueId());
    }

    public int of(Entity entity) {
        Npc npc = plugin.npcs().byEntity(entity);
        if (npc != null) {
            return of(npc);
        }
        if (entity instanceof Player player) {
            return of(player);
        }
        return NONE;
    }

    /** @return {@code true} if both are team mates (same team number) or the same unteamed NPC */
    public boolean allied(Entity a, Entity b) {
        int fa = of(a);
        int fb = of(b);
        return fa == fb && fa != NONE && fa != UNTEAMED_PLAYERS;
    }
}
