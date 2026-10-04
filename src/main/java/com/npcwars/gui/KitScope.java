package com.npcwars.gui;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.npc.Npc;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.entity.Player;

/** Which NPCs a kit is applied to when picked from the menu. */
public record KitScope(Type type, int value) {

    public enum Type { ALL, SELECTED, TEAM, SINGLE }

    public static KitScope all() {
        return new KitScope(Type.ALL, 0);
    }

    public static KitScope selected() {
        return new KitScope(Type.SELECTED, 0);
    }

    public static KitScope team(int team) {
        return new KitScope(Type.TEAM, team);
    }

    public static KitScope single(int npcId) {
        return new KitScope(Type.SINGLE, npcId);
    }

    /** @return the matching NPCs at this moment (selection and team membership can change while a menu is open) */
    public List<Npc> resolve(NpcWarsPlugin plugin, Player viewer) {
        List<Npc> out = new ArrayList<>();
        switch (type) {
            case ALL -> out.addAll(plugin.npcs().all());
            case SELECTED -> addAll(plugin, plugin.npcs().selection().get(viewer.getUniqueId()), out);
            case TEAM -> addAll(plugin, plugin.teams().npcsOf(value), out);
            case SINGLE -> {
                Npc npc = plugin.npcs().get(value);
                if (npc != null) {
                    out.add(npc);
                }
            }
        }
        return out;
    }

    /** Short text for menu titles and messages, e.g. {@code all NPCs} or {@code team 3}. */
    public String describe() {
        return switch (type) {
            case ALL -> "all NPCs";
            case SELECTED -> "selected NPCs";
            case TEAM -> "team " + value;
            case SINGLE -> "NPC #" + value;
        };
    }

    private static void addAll(NpcWarsPlugin plugin, Set<Integer> ids, List<Npc> out) {
        for (int id : ids) {
            Npc npc = plugin.npcs().get(id);
            if (npc != null) {
                out.add(npc);
            }
        }
    }
}
