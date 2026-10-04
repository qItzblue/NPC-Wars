package com.npcwars.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeamManagerTest {

    private TeamManager teams;
    private AtomicInteger changes;

    @BeforeEach
    void setUp() {
        teams = new TeamManager();
        changes = new AtomicInteger();
        teams.setChangeListener(changes::incrementAndGet);
    }

    @Test
    void teamsAreCreatedOnDemandWithAnyPositiveNumber() {
        assertFalse(teams.exists(7));
        teams.addNpc(7, 1);
        teams.addNpc(1_000_000, 2);
        assertTrue(teams.exists(7));
        assertTrue(teams.exists(1_000_000));
        assertEquals(7, teams.teamOfNpc(1));
        assertEquals(1_000_000, teams.teamOfNpc(2));
    }

    @Test
    void rejectsNonPositiveTeamNumbers() {
        assertThrows(IllegalArgumentException.class, () -> teams.addNpc(0, 1));
        assertThrows(IllegalArgumentException.class, () -> teams.addNpc(-3, 1));
        assertThrows(IllegalArgumentException.class, () -> teams.addPlayer(0, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> teams.massAddNpcs(0, List.of(1)));
    }

    @Test
    void memberBelongsToOneTeamAtATime() {
        assertEquals(TeamManager.NO_TEAM, teams.addNpc(1, 10));
        assertEquals(1, teams.addNpc(2, 10));
        assertEquals(2, teams.teamOfNpc(10));
        assertFalse(teams.find(1).orElseThrow().npcIds().contains(10));
        assertTrue(teams.find(2).orElseThrow().npcIds().contains(10));
    }

    @Test
    void playersAndNpcsShareATeam() {
        UUID player = UUID.randomUUID();
        teams.addNpc(3, 5);
        teams.addPlayer(3, player);
        Team team = teams.find(3).orElseThrow();
        assertEquals(2, team.size());
        assertEquals(3, teams.teamOfPlayer(player));
        assertEquals(TeamManager.NO_TEAM, teams.teamOfPlayer(UUID.randomUUID()));
    }

    @Test
    void movingAPlayerLeavesTheOldTeam() {
        UUID player = UUID.randomUUID();
        teams.addPlayer(1, player);
        assertEquals(1, teams.addPlayer(2, player));
        assertTrue(teams.find(1).orElseThrow().playerIds().isEmpty());
        assertEquals(2, teams.teamOfPlayer(player));
    }

    @Test
    void massAddPutsEveryNpcIntoTheTeamAndCountsChanges() {
        teams.addNpc(4, 2);
        int changed = teams.massAddNpcs(4, List.of(1, 2, 3, 4));
        assertEquals(3, changed, "NPC 2 was already in team 4");
        assertEquals(Set.of(1, 2, 3, 4), teams.npcsOf(4));
    }

    @Test
    void massAddMovesNpcsFromOtherTeams() {
        teams.addNpc(1, 1);
        teams.addNpc(2, 2);
        assertEquals(2, teams.massAddNpcs(9, List.of(1, 2)));
        assertTrue(teams.find(1).orElseThrow().npcIds().isEmpty());
        assertTrue(teams.find(2).orElseThrow().npcIds().isEmpty());
        assertEquals(Set.of(1, 2), teams.npcsOf(9));
    }

    @Test
    void massAddFiresOneChangeNotification() {
        changes.set(0);
        teams.massAddNpcs(5, List.of(1, 2, 3, 4, 5, 6));
        assertEquals(1, changes.get());
    }

    @Test
    void removeReportsWhetherTheMemberWasOnATeam() {
        UUID player = UUID.randomUUID();
        teams.addNpc(1, 8);
        teams.addPlayer(1, player);
        assertTrue(teams.removeNpc(8));
        assertFalse(teams.removeNpc(8));
        assertTrue(teams.removePlayer(player));
        assertFalse(teams.removePlayer(player));
        assertEquals(TeamManager.NO_TEAM, teams.teamOfNpc(8));
    }

    @Test
    void renameSetsAndClearsTheAdminName() {
        teams.addNpc(2, 1);
        assertTrue(teams.rename(2, "  Red Squad "));
        assertEquals("Red Squad", teams.find(2).orElseThrow().adminName());
        assertTrue(teams.rename(2, "   "));
        assertNull(teams.find(2).orElseThrow().adminName());
        assertTrue(teams.rename(2, "x".repeat(100)));
        assertEquals(32, teams.find(2).orElseThrow().adminName().length());
        assertFalse(teams.rename(99, "nobody"), "unknown teams cannot be renamed");
    }

    @Test
    void deleteReleasesAllMembers() {
        UUID player = UUID.randomUUID();
        teams.addNpc(6, 1);
        teams.addNpc(6, 2);
        teams.addPlayer(6, player);
        assertTrue(teams.delete(6));
        assertFalse(teams.exists(6));
        assertEquals(TeamManager.NO_TEAM, teams.teamOfNpc(1));
        assertEquals(TeamManager.NO_TEAM, teams.teamOfPlayer(player));
        assertFalse(teams.delete(6));
    }

    @Test
    void pruneDropsOnlyEmptyUnnamedTeams() {
        teams.addNpc(1, 1);
        teams.removeNpc(1);
        teams.addNpc(2, 2);
        teams.removeNpc(2);
        teams.rename(2, "Keep me");
        teams.addNpc(3, 3);
        assertEquals(1, teams.pruneEmpty());
        assertFalse(teams.exists(1));
        assertTrue(teams.exists(2));
        assertTrue(teams.exists(3));
    }

    @Test
    void restoreDoesNotNotifyAndRebuildsIndexes() {
        UUID player = UUID.randomUUID();
        changes.set(0);
        teams.restore(5, "Blue", List.of(1, 2), List.of(player));
        assertEquals(0, changes.get());
        assertEquals(5, teams.teamOfNpc(2));
        assertEquals(5, teams.teamOfPlayer(player));
        assertEquals("Blue", teams.find(5).orElseThrow().adminName());
    }

    @Test
    void teamsAreListedInNumericOrder() {
        teams.addNpc(10, 1);
        teams.addNpc(2, 2);
        teams.addNpc(33, 3);
        List<Integer> order = teams.teams().stream().map(Team::id).toList();
        assertEquals(List.of(2, 10, 33), order);
    }

    @Test
    void npcsOfReturnsADefensiveCopy() {
        teams.addNpc(1, 1);
        Set<Integer> copy = teams.npcsOf(1);
        teams.addNpc(1, 2);
        assertEquals(Set.of(1), copy);
        assertTrue(teams.npcsOf(404).isEmpty());
    }
}
