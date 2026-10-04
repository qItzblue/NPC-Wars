package com.npcwars.command;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.action.ActionContext;
import com.npcwars.action.ActionException;
import com.npcwars.action.NpcAction;
import com.npcwars.action.PreparedAction;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.team.Team;
import com.npcwars.util.Completions;
import com.npcwars.util.TimeParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

/**
 * {@code /massaction <action> [args...] [for=<time>] [team=<n>] [npc=<ids>]}: makes every NPC (or the chosen subset)
 * perform the same action. Actions come from the {@link com.npcwars.action.ActionRegistry}, so new ones appear here
 * and in tab completion without touching this class.
 */
public final class MassActionCommand implements TabExecutor {

    private static final Set<String> FLAGS = Set.of("for", "team", "npc");
    private static final long MAX_DURATION_SECONDS = 24L * 3600L;

    private final NpcWarsPlugin plugin;

    public MassActionCommand(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!sender.hasPermission("npcplugin.massaction")) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        try {
            run(sender, label, List.of(args));
        } catch (CommandException ex) {
            messages.send(sender, ex.messageKey(), ex.resolvers());
        } catch (ActionException ex) {
            messages.send(sender, ex.messageKey(), ex.resolvers());
        }
        return true;
    }

    private void run(CommandSender sender, String label, List<String> args) throws CommandException, ActionException {
        Messages messages = plugin.messages();
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("list") || args.get(0).equalsIgnoreCase("help")) {
            messages.send(sender, "massaction.list-header", Messages.var("label", label));
            for (NpcAction action : plugin.actions().all()) {
                messages.send(sender, "massaction.list-entry", Messages.var("action", action.name()),
                        Messages.var("usage", action.usage()), Messages.var("description", action.description()));
            }
            messages.send(sender, "massaction.list-flags");
            return;
        }

        NpcAction action = plugin.actions().find(args.get(0));
        if (action == null) {
            throw new CommandException("massaction.unknown", Messages.var("input", args.get(0)),
                    Messages.var("actions", String.join(", ", plugin.actions().names())));
        }
        ArgFlags flags = ArgFlags.parse(args.subList(1, args.size()), FLAGS);

        long durationTicks = 0;
        if (flags.has("for")) {
            try {
                long seconds = TimeParser.parseSeconds(flags.get("for"));
                if (seconds <= 0 || seconds > MAX_DURATION_SECONDS) {
                    throw new IllegalArgumentException("out of range");
                }
                durationTicks = seconds * 20L;
            } catch (IllegalArgumentException ex) {
                throw new CommandException("massaction.invalid-duration", Messages.var("input", flags.get("for")));
            }
        }

        List<Npc> targets = selectTargets(sender, flags);
        if (targets.isEmpty()) {
            throw new CommandException("massaction.no-npcs");
        }
        if (plugin.fights().isRunning() && !plugin.settings().allowMassActionDuringFight) {
            throw new CommandException("massaction.during-fight");
        }

        PreparedAction prepared = action.prepare(new ActionContext(plugin, sender, durationTicks), flags.positional());
        for (Npc npc : targets) {
            plugin.runner().start(npc, prepared, durationTicks);
        }
        messages.send(sender, "massaction.started", Messages.var("action", action.name()), Messages.var("count", targets.size()));
    }

    /** Live NPCs, narrowed by {@code npc=} and {@code team=} when given. */
    private List<Npc> selectTargets(CommandSender sender, ArgFlags flags) throws CommandException {
        CommandContext ctx = new CommandContext(plugin, sender, "massaction", List.of());
        Map<Integer, Npc> pool = new LinkedHashMap<>();
        if (flags.has("npc")) {
            for (Npc npc : Targets.many(ctx, flags.get("npc"))) {
                pool.put(npc.id(), npc);
            }
        } else {
            for (Npc npc : plugin.npcs().all()) {
                pool.put(npc.id(), npc);
            }
        }
        if (flags.has("team")) {
            int team = ctx.teamNumber(flags.get("team"));
            Set<Integer> members = plugin.teams().npcsOf(team);
            if (members.isEmpty()) {
                throw new CommandException("team.not-found", Messages.var("team", team));
            }
            pool.keySet().retainAll(members);
        }
        List<Npc> live = new ArrayList<>();
        for (Npc npc : pool.values()) {
            if (npc.isLive()) {
                live.add(npc);
            }
        }
        return live;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("npcplugin.massaction")) {
            return List.of();
        }
        if (args.length <= 1) {
            List<String> names = new ArrayList<>(plugin.actions().names());
            names.add("list");
            return Completions.filter(names, args.length == 0 ? "" : args[0]);
        }
        NpcAction action = plugin.actions().find(args[0]);
        if (action == null) {
            return List.of();
        }
        String last = args[args.length - 1];
        String lower = last.toLowerCase(Locale.ROOT);
        if (lower.startsWith("for=")) {
            return Completions.filter(List.of("for=5s", "for=10s", "for=30s", "for=1m", "for=5m"), last);
        }
        if (lower.startsWith("team=")) {
            List<String> teams = new ArrayList<>();
            for (Team team : plugin.teams().teams()) {
                teams.add("team=" + team.id());
            }
            return Completions.filter(teams, last);
        }
        if (lower.startsWith("npc=")) {
            List<String> ids = new ArrayList<>();
            plugin.npcs().idStrings().forEach(id -> ids.add("npc=" + id));
            return Completions.filter(ids, last);
        }

        List<String> rest = List.of(args).subList(1, args.length);
        ArgFlags flags = ArgFlags.parse(rest, FLAGS);
        List<String> suggestions = new ArrayList<>();
        try {
            suggestions.addAll(action.complete(new ActionContext(plugin, sender, 0), new ArrayList<>(flags.positional())));
        } catch (RuntimeException ex) {
            // A faulty third-party completer must never break the command line.
        }
        for (String flag : List.of("for=", "team=", "npc=")) {
            if (!flags.has(flag.substring(0, flag.length() - 1))) {
                suggestions.add(flag);
            }
        }
        return Completions.filter(suggestions, last);
    }
}
