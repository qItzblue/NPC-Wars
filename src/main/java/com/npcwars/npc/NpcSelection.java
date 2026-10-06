package com.npcwars.npc;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-staff-member set of selected NPC ids (kept in memory only). */
public final class NpcSelection {

    private final Map<UUID, Set<Integer>> selections = new HashMap<>();

    /** @return a read-only view of the ids selected by this staff member */
    public Set<Integer> get(UUID owner) {
        Set<Integer> set = selections.get(owner);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    public void set(UUID owner, Set<Integer> ids) {
        if (ids.isEmpty()) {
            selections.remove(owner);
        } else {
            selections.put(owner, new LinkedHashSet<>(ids));
        }
    }

    public boolean add(UUID owner, int id) {
        return selections.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(id);
    }

    public void clear(UUID owner) {
        selections.remove(owner);
    }

    /** Removes an NPC from everyone's selection (called when the NPC is deleted). */
    public void forget(int npcId) {
        selections.values().forEach(set -> set.remove(npcId));
        selections.values().removeIf(Set::isEmpty);
    }
}
