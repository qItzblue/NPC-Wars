package com.npcwars.command;

import com.npcwars.appearance.Pools;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.util.Completions;
import java.util.ArrayList;
import java.util.List;

/** {@code /npcwars randomize} and {@code /npcwars pool}: random names and skins, and the pools they come from. */
final class AppearanceCommands {

    private AppearanceCommands() {
    }

    static void register(SubCommandRouter router) {
        router.register(randomize()).register(pool());
    }

    private static SubCommand randomize() {
        return SubCommand.of("randomize", "npcplugin.npc", "randomize <id|ids|selected|all|team:<n>> [names|skins|both]",
                "Give NPCs new random names and/or skins from the pools", ctx -> {
                    String what = "both";
                    List<String> targetArgs = ctx.args();
                    String last = ctx.arg(ctx.size() - 1).toLowerCase(java.util.Locale.ROOT);
                    if (last.equals("names") || last.equals("skins") || last.equals("both")) {
                        what = last;
                        targetArgs = ctx.args().subList(0, ctx.size() - 1);
                    }
                    if (targetArgs.isEmpty()) {
                        throw new CommandException("general.usage", Messages.var("usage", ctx.label() + " randomize <targets> [names|skins|both]"));
                    }
                    List<Npc> targets = Targets.manyOf(ctx, targetArgs);
                    boolean names = !what.equals("skins");
                    boolean skins = !what.equals("names");
                    for (Npc npc : targets) {
                        if (names) {
                            ctx.plugin().npcs().setLabel(npc, ctx.plugin().pools().randomName());
                        }
                        if (skins) {
                            ctx.plugin().npcs().setSkin(npc, ctx.plugin().pools().randomSkin());
                        }
                    }
                    ctx.send("appearance.randomized", Messages.var("count", targets.size()), Messages.var("what", what));
                }).minArgs(1).complete(ctx -> {
                    List<String> options = new ArrayList<>(Targets.npcSuggestions(ctx));
                    options.addAll(List.of("names", "skins", "both"));
                    return Completions.filter(options, ctx.last());
                });
    }

    private static SubCommand pool() {
        return SubCommand.of("pool", "npcplugin.npc", "pool <names|skins> <list|add|remove> [value]",
                "Edit the pools random names and skins are drawn from (pools.yml)", ctx -> {
                    Pools.Kind kind = Pools.Kind.parse(ctx.arg(0));
                    if (kind == null || ctx.size() < 2) {
                        throw new CommandException("general.usage", Messages.var("usage", ctx.label() + " pool <names|skins> <list|add|remove> [value]"));
                    }
                    Pools pools = ctx.plugin().pools();
                    String action = ctx.arg(1).toLowerCase(java.util.Locale.ROOT);
                    switch (action) {
                        case "list" -> {
                            List<String> values = pools.list(kind);
                            if (values.isEmpty()) {
                                throw new CommandException("appearance.pool-empty", Messages.var("kind", kind.key()));
                            }
                            ctx.send("appearance.pool-list", Messages.var("kind", kind.key()),
                                    Messages.var("count", values.size()), Messages.var("values", String.join(", ", values)));
                        }
                        case "add", "remove" -> {
                            if (ctx.size() < 3) {
                                throw new CommandException("general.usage", Messages.var("usage", ctx.label() + " pool " + kind.key() + " " + action + " <value>"));
                            }
                            String value = String.join(" ", ctx.args().subList(2, ctx.size())).trim();
                            if (action.equals("add")) {
                                if (!Pools.isValid(kind, value)) {
                                    throw new CommandException("appearance.invalid-value", Messages.var("input", value), Messages.var("kind", kind.key()));
                                }
                                if (!pools.add(kind, value)) {
                                    throw new CommandException("appearance.pool-duplicate", Messages.var("input", value));
                                }
                                ctx.send("appearance.pool-added", Messages.var("input", value), Messages.var("kind", kind.key()));
                            } else {
                                if (!pools.remove(kind, value)) {
                                    throw new CommandException("appearance.pool-missing", Messages.var("input", value), Messages.var("kind", kind.key()));
                                }
                                ctx.send("appearance.pool-removed", Messages.var("input", value), Messages.var("kind", kind.key()));
                            }
                        }
                        default -> throw new CommandException("general.usage", Messages.var("usage", ctx.label() + " pool <names|skins> <list|add|remove> [value]"));
                    }
                }).minArgs(2).complete(ctx -> {
                    if (ctx.size() == 1) {
                        return Completions.filter(List.of("names", "skins"), ctx.last());
                    }
                    if (ctx.size() == 2) {
                        return Completions.filter(List.of("list", "add", "remove"), ctx.last());
                    }
                    Pools.Kind kind = Pools.Kind.parse(ctx.arg(0));
                    if (kind != null && ctx.size() == 3 && ctx.arg(1).equalsIgnoreCase("remove")) {
                        return Completions.filter(ctx.plugin().pools().list(kind), ctx.last());
                    }
                    return List.of();
                });
    }
}
