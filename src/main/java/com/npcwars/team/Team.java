package com.npcwars.team;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A numbered team. The optional {@link #adminName()} exists only so staff can tell teams apart in command output;
 * it is never shown to players (no nametag, chat, scoreboard or announcement uses it).
 */
public final class Team {

    private final int id;
    private String adminName;
    private final Set<Integer> npcIds = new LinkedHashSet<>();
    private final Set<UUID> playerIds = new LinkedHashSet<>();

    Team(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    /** @return the admin-only label, or {@code null} if none was set */
    public String adminName() {
        return adminName;
    }

    void setAdminName(String adminName) {
        this.adminName = adminName;
    }

    /** @return a read-only view of the NPC ids in this team */
    public Set<Integer> npcIds() {
        return Collections.unmodifiableSet(npcIds);
    }

    /** @return a read-only view of the player UUIDs in this team */
    public Set<UUID> playerIds() {
        return Collections.unmodifiableSet(playerIds);
    }

    public int size() {
        return npcIds.size() + playerIds.size();
    }

    Set<Integer> mutableNpcIds() {
        return npcIds;
    }

    Set<UUID> mutablePlayerIds() {
        return playerIds;
    }
}
