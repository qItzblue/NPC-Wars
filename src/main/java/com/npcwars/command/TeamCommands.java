package com.npcwars.command;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.team.Team;
import com.npcwars.team.TeamManager;
import com.npcwars.util.Completions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * {@code /npcwars team ...}. Team numbers are positive integers, teams are created the first time they are used, and a
 * team's optional name is only ever printed by these admin commands.
 */
final class TeamCommands {

    private TeamCommands() {
    }

    static void register(SubCommandRouter root) {
        SubCommandRouter team = new SubCommandRouter();
        team.register(add())
                .register(massAdd())
                .register(remove())
                .register(list())
                .register(info())
                .register(rename())
                .register(delete());
        root.register(SubCommand.of("team", "npcplugin.team", "team <add|massadd|remove|list|info|rename|delete>",
                        "Manage teams", team::dispatch)
                .complete(team::complete));
    }

    private static SubCommand add() {
        return SubCommand.of("add", "npcplugin.team", "add <team> <npc|player>...",
                "Add NPCs or players to a team (the team is created if it does not exist)", ctx -> {
                    int teamId = ctx.teamNumber(ctx.arg(0));
                    TeamManager teams = ctx.plugin().teams();
                    int npcCount = 0;
                    int playerCount = 0;
                    for (String token : ctx.args().subList(1, ctx.size())) {
                        for (Targets.Member member : Targets.members(ctx, token)) {
                            if (member.isNpc()) {
                                teams.addNpc(teamId, member.npc().id());
                                npcCount++;
                            } else {
                                teams.addPlayer(teamId, member.playerId());
                                playerCount++;
                            }
                        }
                    }
                    ctx.send("team.added", Messages.var("team", teamId), Messages.var("npcs", npcCount),
                            Messages.var("players", playerCount));
                }).minArgs(2).complete(ctx -> {
                    if (ctx.size() == 1) {
                        return Completions.filter(teamNumbers(ctx.plugin(), true), ctx.last());
                    }
                    return Completions.filter(memberSuggestions(ctx), ctx.last());
                });
    }

    private static SubCommand massAdd() {
        return SubCommand.of("massadd", "npcplugin.team", "massadd <team>",
                "Put every NPC into the given team (any number is valid)", ctx -> {
                    int teamId = ctx.teamNumber(ctx.arg(0));
                    List<Integer> ids = new ArrayList<>();
                    for (Npc npc : ctx.plugin().npcs().all()) {
                        ids.add(npc.id());
                    }
                    if (ids.isEmpty()) {
                        throw new CommandException("npc.none-exist");
                    }
                    int changed = ctx.plugin().teams().massAddNpcs(teamId, ids);
                    ctx.send("team.massadded", Messages.var("team", teamId), Messages.var("total", ids.size()),
                            Messages.var("changed", changed));
                }).minArgs(1).complete(ctx -> ctx.size() == 1 ? Completions.filter(teamNumbers(ctx.plugin(), true), ctx.last()) : List.of());
    }

    private static SubCommand remove() {
        return SubCommand.of("remove", "npcplugin.team", "remove <npc|player>...", "Take NPCs or players out of their team", ctx -> {
            TeamManager teams = ctx.plugin().teams();
            int removed = 0;
            for (String token : ctx.args()) {
                for (Targets.Member member : Targets.members(ctx, token)) {
                    boolean was = member.isNpc() ? teams.removeNpc(member.npc().id()) : teams.removePlayer(member.playerId());
                    if (was) {
                        removed++;
                    }
                }
            }
            ctx.send("team.removed", Messages.var("count", removed));
        }).minArgs(1).complete(ctx -> Completions.filter(memberSuggestions(ctx), ctx.last()));
    }

    private static SubCommand list() {
        return SubCommand.of("list", "npcplugin.team", "list", "List all teams", ctx -> {
            var teams = ctx.plugin().teams().teams();
            if (teams.isEmpty()) {
                ctx.send("team.none");
                return;
            }
            ctx.send("team.list-header", Messages.var("count", teams.size()));
            for (Team team : teams) {
                ctx.send("team.list-entry", Messages.var("team", team.id()),
                        Messages.var("name", team.adminName() == null ? "-" : team.adminName()),
                        Messages.var("npcs", team.npcIds().size()), Messages.var("players", team.playerIds().size()));
            }
        });
    }

    private static SubCommand info() {
        return SubCommand.of("info", "npcplugin.team", "info <team>", "Show the members of a team", ctx -> {
            Team team = existing(ctx, ctx.teamNumber(ctx.arg(0)));
            List<String> npcs = new ArrayList<>();
            team.npcIds().forEach(id -> npcs.add(Integer.toString(id)));
            List<String> players = new ArrayList<>();
            for (UUID id : team.playerIds()) {
                Player online = Bukkit.getPlayer(id);
                OfflinePlayer offline = online != null ? online : Bukkit.getOfflinePlayer(id);
                players.add(offline.getName() == null ? id.toString() : offline.getName());
            }
            ctx.send("team.info", Messages.var("team", team.id()),
                    Messages.var("name", team.adminName() == null ? "-" : team.adminName()),
                    Messages.var("npcs", npcs.isEmpty() ? "-" : String.join(", ", npcs)),
                    Messages.var("players", players.isEmpty() ? "-" : String.join(", ", players)));
        }).minArgs(1).complete(ctx -> ctx.size() == 1 ? Completions.filter(teamNumbers(ctx.plugin(), false), ctx.last()) : List.of());
    }

    private static SubCommand rename() {
        return SubCommand.of("rename", "npcplugin.team", "rename <team> <name...|clear>",
                "Set an admin-only name for a team (never shown in game)", ctx -> {
                    Team team = existing(ctx, ctx.teamNumber(ctx.arg(0)));
                    String name = String.join(" ", ctx.args().subList(1, ctx.size()));
                    boolean clear = name.equalsIgnoreCase("clear");
                    ctx.plugin().teams().rename(team.id(), clear ? null : name);
                    if (clear) {
                        ctx.send("team.name-cleared", Messages.var("team", team.id()));
                    } else {
                        ctx.send("team.renamed", Messages.var("team", team.id()),
                                Messages.var("name", team.adminName() == null ? "-" : team.adminName()));
                    }
                }).minArgs(2).complete(ctx -> {
            if (ctx.size() == 1) {
                return Completions.filter(teamNumbers(ctx.plugin(), false), ctx.last());
            }
            return ctx.size() == 2 ? Completions.filter(List.of("clear"), ctx.last()) : List.of();
        });
    }

    private static SubCommand delete() {
        return SubCommand.of("delete", "npcplugin.team", "delete <team>", "Delete a team; its members become team-less", ctx -> {
            int teamId = ctx.teamNumber(ctx.arg(0));
            if (!ctx.plugin().teams().delete(teamId)) {
                throw new CommandException("team.not-found", Messages.var("team", teamId));
            }
            ctx.send("team.deleted", Messages.var("team", teamId));
        }).minArgs(1).complete(ctx -> ctx.size() == 1 ? Completions.filter(teamNumbers(ctx.plugin(), false), ctx.last()) : List.of());
    }

    private static Team existing(CommandContext ctx, int teamId) throws CommandException {
        return ctx.plugin().teams().find(teamId)
                .orElseThrow(() -> new CommandException("team.not-found", Messages.var("team", teamId)));
    }

    /** Existing team numbers, plus {@code 1} (or the next free number) when new teams may be created. */
    private static List<String> teamNumbers(NpcWarsPlugin plugin, boolean includeNew) {
        List<String> out = new ArrayList<>();
        int highest = 0;
        for (Team team : plugin.teams().teams()) {
            out.add(Integer.toString(team.id()));
            highest = Math.max(highest, team.id());
        }
        if (includeNew) {
            out.add(Integer.toString(highest + 1));
        }
        return out;
    }

    private static List<String> memberSuggestions(CommandContext ctx) {
        List<String> out = new ArrayList<>(Targets.npcSuggestions(ctx));
        Bukkit.getOnlinePlayers().forEach(player -> out.add(player.getName()));
        return out;
    }
}
