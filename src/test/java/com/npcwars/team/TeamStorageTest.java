package com.npcwars.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class TeamStorageTest {

    private static final Logger LOG = Logger.getLogger("test");

    @Test
    void roundTripsThroughYaml() throws InvalidConfigurationException {
        TeamManager source = new TeamManager();
        UUID player = UUID.randomUUID();
        source.addNpc(3, 1);
        source.addNpc(3, 2);
        source.addPlayer(3, player);
        source.addNpc(12, 7);
        source.rename(12, "Admins only");

        YamlConfiguration out = new YamlConfiguration();
        TeamStorage.save(source, out);
        YamlConfiguration in = new YamlConfiguration();
        in.loadFromString(out.saveToString());

        TeamManager loaded = new TeamManager();
        TeamStorage.load(loaded, in, LOG);

        assertEquals(Set.of(1, 2), loaded.npcsOf(3));
        assertEquals(3, loaded.teamOfPlayer(player));
        assertEquals(12, loaded.teamOfNpc(7));
        assertEquals("Admins only", loaded.find(12).orElseThrow().adminName());
        assertNull(loaded.find(3).orElseThrow().adminName());
    }

    @Test
    void emptyUnnamedTeamsAreNotWritten() {
        TeamManager source = new TeamManager();
        source.addNpc(1, 1);
        source.removeNpc(1);
        YamlConfiguration out = new YamlConfiguration();
        TeamStorage.save(source, out);
        assertFalse(out.getConfigurationSection("teams").contains("1"));
    }

    @Test
    void loadSkipsBrokenEntriesInsteadOfFailing() throws InvalidConfigurationException {
        YamlConfiguration in = new YamlConfiguration();
        in.loadFromString("""
                teams:
                  abc:
                    npcs: [1]
                  -4:
                    npcs: [2]
                  5:
                    npcs: [3]
                    players: ['not-a-uuid']
                """);
        TeamManager loaded = new TeamManager();
        TeamStorage.load(loaded, in, LOG);
        assertEquals(List.of(5), loaded.teams().stream().map(Team::id).toList());
        assertEquals(5, loaded.teamOfNpc(3));
    }
}
