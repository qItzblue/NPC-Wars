package com.npcwars.team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Registry of numbered teams. Teams are created on demand; a member (NPC id or player UUID) belongs to at most one
 * team, so adding it to another team moves it. This class is pure Java and must only be used from the main thread.
 */
public final class TeamManager {

    /** Returned by the {@code teamOf...} lookups when the member is not on a team. */
    public static final int NO_TEAM = 0;
    private static final int MAX_NAME_LENGTH = 32;

    private final Map<Integer, Team> teams = new TreeMap<>();
    private final Map<Integer, Integer> npcTeam = new HashMap<>();
    private final Map<UUID, Integer> playerTeam = new HashMap<>();
    private Runnable changeListener = () -> { };

    /** Registers a callback that fires after every mutation (used to schedule a save). */
    public void setChangeListener(Runnable listener) {
        this.changeListener = listener == null ? () -> { } : listener;
    }

    /** @throws IllegalArgumentException if {@code id} is not a positive number */
    public Team getOrCreate(int id) {
        requireValidId(id);
        return teams.computeIfAbsent(id, Team::new);
    }

    public Optional<Team> find(int id) {
        return Optional.ofNullable(teams.get(id));
    }

    public boolean exists(int id) {
        return teams.containsKey(id);
    }

    /** @return all teams ordered by number */
    public Collection<Team> teams() {
        return Collections.unmodifiableCollection(teams.values());
    }

    public int teamOfNpc(int npcId) {
        return npcTeam.getOrDefault(npcId, NO_TEAM);
    }

    public int teamOfPlayer(UUID playerId) {
        return playerTeam.getOrDefault(playerId, NO_TEAM);
    }

    /**
     * Puts an NPC into a team, creating the team if needed.
     *
     * @return the team the NPC was in before, or {@link #NO_TEAM}
     */
    public int addNpc(int teamId, int npcId) {
        int previous = moveNpc(teamId, npcId);
        changeListener.run();
        return previous;
    }

    /**
     * Puts a player into a team, creating the team if needed.
     *
     * @return the team the player was in before, or {@link #NO_TEAM}
     */
    public int addPlayer(int teamId, UUID playerId) {
        requireValidId(teamId);
        int previous = teamOfPlayer(playerId);
        if (previous != NO_TEAM && previous != teamId) {
            teams.get(previous).mutablePlayerIds().remove(playerId);
        }
        getOrCreate(teamId).mutablePlayerIds().add(playerId);
        playerTeam.put(playerId, teamId);
        changeListener.run();
        return previous;
    }

    /**
     * Adds every given NPC to a team in one step.
     *
     * @return how many NPCs were newly added or moved (NPCs already in the team are not counted)
     */
    public int massAddNpcs(int teamId, Collection<Integer> npcIds) {
        requireValidId(teamId);
        getOrCreate(teamId);
        int changed = 0;
        for (int npcId : npcIds) {
            if (moveNpc(teamId, npcId) != teamId) {
                changed++;
            }
        }
        changeListener.run();
        return changed;
    }

    /** @return {@code true} if the NPC was on a team */
    public boolean removeNpc(int npcId) {
        Integer previous = npcTeam.remove(npcId);
        if (previous == null) {
            return false;
        }
        Team team = teams.get(previous);
        if (team != null) {
            team.mutableNpcIds().remove(npcId);
        }
        changeListener.run();
        return true;
    }

    /** @return {@code true} if the player was on a team */
    public boolean removePlayer(UUID playerId) {
        Integer previous = playerTeam.remove(playerId);
        if (previous == null) {
            return false;
        }
        Team team = teams.get(previous);
        if (team != null) {
            team.mutablePlayerIds().remove(playerId);
        }
        changeListener.run();
        return true;
    }

    /**
     * Sets or clears the admin-only name of an existing team.
     *
     * @param name the new name, or {@code null}/blank to clear it
     * @return {@code false} if the team does not exist
     */
    public boolean rename(int teamId, String name) {
        Team team = teams.get(teamId);
        if (team == null) {
            return false;
        }
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.length() > MAX_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_NAME_LENGTH);
        }
        team.setAdminName(cleaned.isEmpty() ? null : cleaned);
        changeListener.run();
        return true;
    }

    /** Deletes a team; its members become team-less. */
    public boolean delete(int teamId) {
        Team team = teams.remove(teamId);
        if (team == null) {
            return false;
        }
        for (int npcId : team.npcIds()) {
            npcTeam.remove(npcId);
        }
        for (UUID playerId : team.playerIds()) {
            playerTeam.remove(playerId);
        }
        changeListener.run();
        return true;
    }

    /** @return a copy of the NPC ids in a team (empty if the team does not exist) */
    public Set<Integer> npcsOf(int teamId) {
        Team team = teams.get(teamId);
        return team == null ? Set.of() : Set.copyOf(team.npcIds());
    }

    /** Drops teams that have neither members nor an admin name; returns how many were removed. */
    public int pruneEmpty() {
        List<Integer> remove = new ArrayList<>();
        for (Team team : teams.values()) {
            if (team.size() == 0 && team.adminName() == null) {
                remove.add(team.id());
            }
        }
        remove.forEach(teams::remove);
        return remove.size();
    }

    /** Loads a team from disk without firing the change listener. */
    public void restore(int id, String adminName, Collection<Integer> npcIds, Collection<UUID> playerIds) {
        if (id <= 0) {
            return;
        }
        Team team = getOrCreate(id);
        team.setAdminName(adminName == null || adminName.isBlank() ? null : adminName.trim());
        for (int npcId : npcIds) {
            int previous = npcTeam.getOrDefault(npcId, NO_TEAM);
            if (previous != NO_TEAM && previous != id) {
                teams.get(previous).mutableNpcIds().remove(npcId);
            }
            team.mutableNpcIds().add(npcId);
            npcTeam.put(npcId, id);
        }
        for (UUID playerId : playerIds) {
            int previous = playerTeam.getOrDefault(playerId, NO_TEAM);
            if (previous != NO_TEAM && previous != id) {
                teams.get(previous).mutablePlayerIds().remove(playerId);
            }
            team.mutablePlayerIds().add(playerId);
            playerTeam.put(playerId, id);
        }
    }

    /** Removes everything without firing the change listener. */
    public void clear() {
        teams.clear();
        npcTeam.clear();
        playerTeam.clear();
    }

    private int moveNpc(int teamId, int npcId) {
        requireValidId(teamId);
        int previous = teamOfNpc(npcId);
        if (previous != NO_TEAM && previous != teamId) {
            teams.get(previous).mutableNpcIds().remove(npcId);
        }
        getOrCreate(teamId).mutableNpcIds().add(npcId);
        npcTeam.put(npcId, teamId);
        return previous;
    }

    private static void requireValidId(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("team numbers start at 1");
        }
    }
}
