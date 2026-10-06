package com.npcwars.command;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Messages;
import com.npcwars.dependency.DependencyInstaller;
import com.npcwars.dependency.DependencyInstaller.Outcome;
import com.npcwars.dependency.PluginSource;
import com.npcwars.integration.CitizensImporter;
import com.npcwars.util.Completions;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/** {@code /npcwars deps} (download Citizens / EssentialsX) and {@code /npcwars import citizens}. */
final class IntegrationCommands {

    private IntegrationCommands() {
    }

    static void register(SubCommandRouter router) {
        router.register(deps()).register(importer());
    }

    // ---------------------------------------------------------------- deps

    private static SubCommand deps() {
        return SubCommand.of("deps", "npcplugin.deps", "deps [install [citizens|essentialsx|all]]",
                "Show or download the optional plugins (Citizens, EssentialsX)", ctx -> {
                    DependencyInstaller installer = ctx.plugin().dependencies();
                    if (ctx.size() == 0 || ctx.arg(0).equalsIgnoreCase("status")) {
                        ctx.send("deps.status-header");
                        for (PluginSource source : installer.sources()) {
                            ctx.send("deps.status-line", Messages.var("plugin", source.pluginName()),
                                    Messages.var("state", installer.isLoaded(source) ? "loaded" : "not loaded"));
                        }
                        return;
                    }
                    if (!ctx.arg(0).equalsIgnoreCase("install")) {
                        throw new CommandException("general.usage", Messages.var("usage", ctx.label() + " deps [install [citizens|essentialsx|all]]"));
                    }
                    List<PluginSource> wanted = new ArrayList<>();
                    String which = ctx.size() > 1 ? ctx.arg(1) : "all";
                    if (which.equalsIgnoreCase("all")) {
                        wanted.addAll(installer.sources());
                    } else {
                        PluginSource source = installer.source(which);
                        if (source == null) {
                            throw new CommandException("deps.unknown", Messages.var("input", which));
                        }
                        wanted.add(source);
                    }
                    startInstall(ctx.plugin(), ctx.sender(), wanted);
                }).complete(ctx -> {
                    if (ctx.size() == 1) {
                        return Completions.filter(List.of("status", "install"), ctx.last());
                    }
                    if (ctx.size() == 2 && ctx.arg(0).equalsIgnoreCase("install")) {
                        return Completions.filter(List.of("citizens", "essentialsx", "all"), ctx.last());
                    }
                    return List.of();
                });
    }

    /** Starts a download and reports the outcome to the sender (and the console). */
    static void startInstall(NpcWarsPlugin plugin, CommandSender sender, List<PluginSource> wanted) throws CommandException {
        DependencyInstaller installer = plugin.dependencies();
        boolean started = installer.install(wanted, outcomes -> {
            for (Outcome outcome : outcomes) {
                report(plugin, sender, outcome);
            }
        });
        if (!started) {
            throw new CommandException("deps.busy");
        }
        plugin.messages().send(sender, "deps.started");
    }

    private static void report(NpcWarsPlugin plugin, CommandSender sender, Outcome outcome) {
        String key = switch (outcome.status()) {
            case INSTALLED -> "deps.installed";
            case PRESENT -> "deps.present";
            case UNAVAILABLE -> "deps.unavailable";
            case FAILED -> "deps.failed";
        };
        plugin.messages().send(sender, key, Messages.var("plugin", outcome.source().pluginName()),
                Messages.var("detail", outcome.detail()));
    }

    // ---------------------------------------------------------------- import

    private static SubCommand importer() {
        return SubCommand.of("import", "npcplugin.import", "import citizens [all|<ids>]",
                "Copy player NPCs from Citizens into NPC-Wars (the Citizens NPCs stay)", ctx -> {
                    if (!ctx.arg(0).equalsIgnoreCase("citizens")) {
                        throw new CommandException("general.usage", Messages.var("usage", ctx.label() + " import citizens [all|<ids>]"));
                    }
                    if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
                        throw new CommandException("import.citizens-missing");
                    }
                    Set<Integer> ids = ctx.size() < 2 || ctx.arg(1).equalsIgnoreCase("all") ? null : parseIds(ctx.arg(1));
                    CitizensImporter.Result result = CitizensImporter.importNpcs(ctx.plugin(), ids);
                    if (result.imported() == 0 && !result.limitReached()) {
                        throw new CommandException("import.nothing", Messages.var("skipped", result.skipped()));
                    }
                    ctx.send("import.done", Messages.var("count", result.imported()), Messages.var("skipped", result.skipped()));
                    if (result.limitReached()) {
                        ctx.send("import.limit", Messages.var("max", ctx.plugin().settings().maxNpcs));
                    }
                }).minArgs(1).complete(ctx -> {
                    if (ctx.size() == 1) {
                        return Completions.filter(List.of("citizens"), ctx.last());
                    }
                    return ctx.size() == 2 ? Completions.filter(List.of("all"), ctx.last()) : List.of();
                });
    }

    /** Parses {@code 3}, {@code 1,2,7} and {@code 5-9} (any mix, separated by commas). */
    static Set<Integer> parseIds(String text) throws CommandException {
        Set<Integer> ids = new LinkedHashSet<>();
        for (String part : text.split(",")) {
            try {
                int dash = part.indexOf('-');
                if (dash > 0) {
                    int from = Integer.parseInt(part.substring(0, dash));
                    int to = Integer.parseInt(part.substring(dash + 1));
                    if (from > to || to - from > 10_000) {
                        throw new NumberFormatException();
                    }
                    for (int id = from; id <= to; id++) {
                        ids.add(id);
                    }
                } else {
                    ids.add(Integer.parseInt(part));
                }
            } catch (NumberFormatException ex) {
                throw new CommandException("import.invalid-ids", Messages.var("input", text));
            }
        }
        return ids;
    }
}
