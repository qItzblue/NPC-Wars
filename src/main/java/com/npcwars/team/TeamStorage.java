package com.npcwars.team;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/** Reads and writes the {@code teams} section of data.yml. */
public final class TeamStorage {

    private TeamStorage() {
    }

    public static void save(TeamManager manager, ConfigurationSection root) {
        manager.pruneEmpty();
        root.set("teams", null);
        ConfigurationSection teams = root.createSection("teams");
        for (Team team : manager.teams()) {
            ConfigurationSection section = teams.createSection(Integer.toString(team.id()));
            if (team.adminName() != null) {
                section.set("name", team.adminName());
            }
            section.set("npcs", new ArrayList<>(team.npcIds()));
            List<String> players = new ArrayList<>();
            for (UUID playerId : team.playerIds()) {
                players.add(playerId.toString());
            }
            section.set("players", players);
        }
    }

    public static void load(TeamManager manager, ConfigurationSection root, Logger log) {
        manager.clear();
        ConfigurationSection teams = root.getConfigurationSection("teams");
        if (teams == null) {
            return;
        }
        for (String key : teams.getKeys(false)) {
            int id;
            try {
                id = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                log.warning("Ignoring team with invalid number '" + key + "' in data.yml");
                continue;
            }
            ConfigurationSection section = teams.getConfigurationSection(key);
            if (section == null || id <= 0) {
                continue;
            }
            List<UUID> players = new ArrayList<>();
            for (String raw : section.getStringList("players")) {
                try {
                    players.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ex) {
                    log.warning("Ignoring invalid player UUID '" + raw + "' in team " + id);
                }
            }
            manager.restore(id, section.getString("name"), section.getIntegerList("npcs"), players);
        }
    }
}
