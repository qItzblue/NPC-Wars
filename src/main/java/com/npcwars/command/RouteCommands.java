package com.npcwars.command;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.npc.control.NpcController;
import com.npcwars.route.Route;
import com.npcwars.route.RouteManager;
import com.npcwars.route.RouteRunner;
import com.npcwars.util.Completions;
import com.npcwars.util.TimeParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /npc path ...}: build a route out of waypoints, send NPCs along it, and optionally start the fight once
 * everyone has reached the end.
 */
final class RouteCommands {

    private static final long MAX_DELAY_SECONDS = 30L * 86_400L;

    private RouteCommands() {
    }

    static void register(SubCommandRouter root) {
        SubCommandRouter path = new SubCommandRouter();
        path.register(create()).register(add()).register(remove()).register(clear()).register(delete())
                .register(list()).register(info()).register(run()).register(stop());
        root.register(SubCommand.of("path", "npcplugin.route",
                        "path <create|add|remove|clear|delete|list|info|run|stop>", "Waypoint routes NPCs can walk, ending in a fight", path::dispatch)
                .aliases("route").complete(path::complete));
    }

    // ---------------------------------------------------------------- building

    private static SubCommand create() {
        return SubCommand.of("create", "npcplugin.route", "create <name>",
                "Create an empty route in your world (add waypoints with /npc path add)", ctx -> {
                    String name = ctx.arg(0);
                    if (!RouteManager.validName(name)) {
                        throw new CommandException("route.invalid-name", Messages.var("input", name));
                    }
                    World world = ctx.sender() instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
                    if (ctx.plugin().routes().create(name, world.getName()) == null) {
                        throw new CommandException("route.exists", Messages.var("route", name));
                    }
                    ctx.send("route.created", Messages.var("route", name), Messages.var("world", world.getName()));
                }).minArgs(1);
    }

    private static SubCommand add() {
        return SubCommand.of("add", "npcplugin.route", "add <name> [x y z]",
                "Add a waypoint where you stand (or at the given coordinates)", ctx -> {
                    Route route = route(ctx, ctx.arg(0));
                    Route.Point point;
                    if (ctx.size() >= 4) {
                        point = new Route.Point(coordinate(ctx, 1), coordinate(ctx, 2), coordinate(ctx, 3));
                    } else if (ctx.sender() instanceof Player player) {
                        if (!player.getWorld().getName().equals(route.worldName())) {
                            throw new CommandException("route.world-mismatch", Messages.var("route", route.name()),
                                    Messages.var("world", route.worldName()));
                        }
                        Location at = player.getLocation();
                        point = new Route.Point(at.getX(), at.getY(), at.getZ());
                    } else {
                        throw new CommandException("general.usage", Messages.var("usage", ctx.label() + " add <name> <x> <y> <z>"));
                    }
                    route.add(point);
                    ctx.plugin().routes().changed();
                    ctx.send("route.point-added", Messages.var("route", route.name()), Messages.var("index", route.size()),
                            Messages.var("where", String.format(Locale.ROOT, "%.1f %.1f %.1f", point.x(), point.y(), point.z())));
                }).minArgs(1).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().routes().names(), ctx.last()) : List.of());
    }

    private static SubCommand remove() {
        return SubCommand.of("remove", "npcplugin.route", "remove <name> <waypoint number>", "Remove one waypoint", ctx -> {
            Route route = route(ctx, ctx.arg(0));
            int index;
            try {
                index = Integer.parseInt(ctx.arg(1));
            } catch (NumberFormatException ex) {
                index = -1;
            }
            if (!route.remove(index)) {
                throw new CommandException("route.invalid-index", Messages.var("input", ctx.arg(1)), Messages.var("max", route.size()));
            }
            ctx.plugin().routes().changed();
            ctx.send("route.point-removed", Messages.var("route", route.name()), Messages.var("index", index));
        }).minArgs(2).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().routes().names(), ctx.last()) : List.of());
    }

    private static SubCommand clear() {
        return SubCommand.of("clear", "npcplugin.route", "clear <name>", "Remove every waypoint but keep the route", ctx -> {
            Route route = route(ctx, ctx.arg(0));
            ctx.plugin().routeRunner().cancel(route);
            route.clear();
            ctx.plugin().routes().changed();
            ctx.send("route.cleared", Messages.var("route", route.name()));
        }).minArgs(1).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().routes().names(), ctx.last()) : List.of());
    }

    private static SubCommand delete() {
        return SubCommand.of("delete", "npcplugin.route", "delete <name>", "Delete a route", ctx -> {
            Route route = route(ctx, ctx.arg(0));
            ctx.plugin().routeRunner().cancel(route);
            ctx.plugin().routes().delete(route.name());
            ctx.send("route.deleted", Messages.var("route", route.name()));
        }).minArgs(1).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().routes().names(), ctx.last()) : List.of());
    }

    // ---------------------------------------------------------------- inspecting

    private static SubCommand list() {
        return SubCommand.of("list", "npcplugin.route", "list", "List the routes", ctx -> {
            List<Route> routes = ctx.plugin().routes().all();
            if (routes.isEmpty()) {
                throw new CommandException("route.none");
            }
            ctx.send("route.list-header", Messages.var("count", routes.size()));
            for (Route route : routes) {
                ctx.send("route.list-entry", Messages.var("route", route.name()), Messages.var("points", route.size()),
                        Messages.var("world", route.worldName()));
            }
        });
    }

    private static SubCommand info() {
        return SubCommand.of("info", "npcplugin.route", "info <name>", "Show a route's waypoints", ctx -> {
            Route route = route(ctx, ctx.arg(0));
            ctx.send("route.info", Messages.var("route", route.name()), Messages.var("points", route.size()),
                    Messages.var("world", route.worldName()),
                    Messages.var("length", String.format(Locale.ROOT, "%.0f", route.length())));
            int number = 1;
            for (Route.Point point : route.points()) {
                ctx.send("route.info-point", Messages.var("index", number++),
                        Messages.var("where", String.format(Locale.ROOT, "%.1f %.1f %.1f", point.x(), point.y(), point.z())));
            }
        }).minArgs(1).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().routes().names(), ctx.last()) : List.of());
    }

    // ---------------------------------------------------------------- running

    private static SubCommand run() {
        return SubCommand.of("run", "npcplugin.route",
                "run <name> [npcs|all|team:<n>] [speed=walk|run] [spread=<blocks>] [fight=now|<time>]",
                "Send NPCs along a route; with fight=... the fight starts when the last one arrives", ctx -> {
                    NpcWarsPlugin plugin = ctx.plugin();
                    Route route = route(ctx, ctx.arg(0));
                    if (route.size() == 0) {
                        throw new CommandException("route.empty", Messages.var("route", route.name()));
                    }
                    ArgFlags flags = ArgFlags.parse(ctx.args().subList(1, ctx.size()), Set.of("speed", "spread", "fight"));
                    NpcController.Gait gait = NpcController.Gait.WALK;
                    if (flags.has("speed")) {
                        switch (flags.get("speed").toLowerCase(Locale.ROOT)) {
                            case "walk" -> gait = NpcController.Gait.WALK;
                            case "run", "sprint" -> gait = NpcController.Gait.SPRINT;
                            default -> throw new CommandException("route.invalid-speed", Messages.var("input", flags.get("speed")));
                        }
                    }
                    double spread = 1.5;
                    if (flags.has("spread")) {
                        try {
                            spread = Math.max(0.0, Math.min(8.0, Double.parseDouble(flags.get("spread"))));
                        } catch (NumberFormatException ex) {
                            throw new CommandException("route.invalid-spread", Messages.var("input", flags.get("spread")));
                        }
                    }
                    RouteRunner.Finish finish = RouteRunner.Finish.NOTHING;
                    long delay = 0;
                    if (flags.has("fight")) {
                        String when = flags.get("fight");
                        if (when.equalsIgnoreCase("now")) {
                            finish = RouteRunner.Finish.FIGHT_NOW;
                        } else {
                            try {
                                delay = TimeParser.parseSeconds(when);
                            } catch (IllegalArgumentException ex) {
                                throw new CommandException("fight.invalid-time", Messages.var("input", when));
                            }
                            if (delay <= 0 || delay > MAX_DELAY_SECONDS) {
                                throw new CommandException("fight.time-range", Messages.var("input", when));
                            }
                            finish = RouteRunner.Finish.FIGHT_AFTER;
                        }
                    }
                    List<Npc> targets = flags.positional().isEmpty()
                            ? new ArrayList<>(plugin.npcs().all()) : Targets.manyOf(ctx, flags.positional());
                    int started = plugin.routeRunner().start(route, targets, new RouteRunner.Options(gait, spread, finish, delay));
                    if (started == 0) {
                        throw new CommandException("route.nobody", Messages.var("route", route.name()));
                    }
                    ctx.send("route.run-started", Messages.var("count", started), Messages.var("route", route.name()),
                            Messages.var("then", finish == RouteRunner.Finish.NOTHING ? "stop at the end" : "start the fight at the end"));
                }).minArgs(1).complete(ctx -> {
                    if (ctx.size() == 1) {
                        return Completions.filter(ctx.plugin().routes().names(), ctx.last());
                    }
                    List<String> options = new ArrayList<>(Targets.npcSuggestions(ctx));
                    options.addAll(List.of("speed=walk", "speed=run", "spread=", "fight=now", "fight=5s", "fight=30s"));
                    return Completions.filter(options, ctx.last());
                });
    }

    private static SubCommand stop() {
        return SubCommand.of("stop", "npcplugin.route", "stop [name]", "Stop NPCs walking a route (no fight is started)", ctx -> {
            int stopped = ctx.size() == 0 ? ctx.plugin().routeRunner().cancelAll()
                    : ctx.plugin().routeRunner().cancel(route(ctx, ctx.arg(0)));
            ctx.send("route.stopped", Messages.var("count", stopped));
        }).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().routes().names(), ctx.last()) : List.of());
    }

    // ---------------------------------------------------------------- helpers

    private static Route route(CommandContext ctx, String name) throws CommandException {
        Route route = ctx.plugin().routes().get(name);
        if (route == null) {
            throw new CommandException("route.not-found", Messages.var("route", name));
        }
        return route;
    }

    private static double coordinate(CommandContext ctx, int index) throws CommandException {
        try {
            double value = Double.parseDouble(ctx.arg(index));
            if (Double.isFinite(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new CommandException("route.invalid-coordinate", Messages.var("input", ctx.arg(index)));
    }
}
