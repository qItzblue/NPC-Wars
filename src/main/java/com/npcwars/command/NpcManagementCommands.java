package com.npcwars.command;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Messages;
import com.npcwars.gui.EquipmentGui;
import com.npcwars.npc.Npc;
import com.npcwars.util.Completions;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** {@code /npc} sub-commands that create, find, move and style NPCs. */
final class NpcManagementCommands {

    private static final Pattern SKIN_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int PAGE_SIZE = 10;
    private static final int MAX_SPAWN_MANY = 200;
    private static final int MAX_LABEL_LENGTH = 32;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private NpcManagementCommands() {
    }

    static void register(SubCommandRouter router) {
        router.register(spawn())
                .register(spawnMany())
                .register(remove())
                .register(removeAll())
                .register(list())
                .register(info())
                .register(teleportTo())
                .register(teleportHere())
                .register(select())
                .register(skin())
                .register(rename())
                .register(equip())
                .register(heal());
    }

    // ---------------------------------------------------------------- spawning

    private static SubCommand spawn() {
        return SubCommand.of("spawn", "npcplugin.npc", "spawn [label] [skin=<player>] [team=<n>]",
                "Spawn an NPC where you stand", ctx -> {
                    Player player = ctx.player();
                    ArgFlags flags = ArgFlags.parse(ctx.args(), Set.of("skin", "team"));
                    String label = cleanLabel(String.join(" ", flags.positional()));
                    String skin = skinOrNull(flags.get("skin"));
                    int team = flags.has("team") ? ctx.teamNumber(flags.get("team")) : 0;
                    Location where = player.getLocation();
                    where.setPitch(0f);
                    Npc npc = create(ctx.plugin(), where, label, skin);
                    if (team > 0) {
                        ctx.plugin().teams().addNpc(team, npc.id());
                    }
                    ctx.send("npc.spawned", Messages.var("id", npc.id()));
                }).complete(ctx -> flagSuggestions(ctx, List.of("skin=", "team=")));
    }

    private static SubCommand spawnMany() {
        return SubCommand.of("spawnmany", "npcplugin.npc", "spawnmany <count> [radius] [skin=<player>] [team=<n>]",
                "Spawn many NPCs spread around you", ctx -> {
                    Player player = ctx.player();
                    ArgFlags flags = ArgFlags.parse(ctx.args(), Set.of("skin", "team"));
                    List<String> words = flags.positional();
                    if (words.isEmpty()) {
                        throw new CommandException("general.usage", Messages.var("usage", "/" + ctx.label() + " spawnmany <count> [radius]"));
                    }
                    int count = parseInt(words.get(0), 1, MAX_SPAWN_MANY, "npc.invalid-count",
                            Messages.var("max", MAX_SPAWN_MANY));
                    double radius = Math.max(2.0, Math.ceil(Math.sqrt(count) * 1.2));
                    if (words.size() > 1) {
                        radius = parseInt(words.get(1), 1, 100, "npc.invalid-radius", Messages.var("max", 100));
                    }
                    String skin = skinOrNull(flags.get("skin"));
                    int team = flags.has("team") ? ctx.teamNumber(flags.get("team")) : 0;
                    NpcWarsPlugin plugin = ctx.plugin();
                    if (plugin.npcs().count() + count > plugin.settings().maxNpcs) {
                        throw new CommandException("npc.limit-reached", Messages.var("max", plugin.settings().maxNpcs));
                    }
                    Location center = player.getLocation();
                    World world = center.getWorld();
                    for (int i = 0; i < count; i++) {
                        double distance = radius * Math.sqrt((i + 0.5) / count);
                        double angle = i * GOLDEN_ANGLE;
                        double x = center.getX() + Math.cos(angle) * distance;
                        double z = center.getZ() + Math.sin(angle) * distance;
                        double y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1.0;
                        if (Math.abs(y - center.getY()) > 8) {
                            y = center.getY();
                        }
                        float yaw = (float) Math.toDegrees(Math.atan2(-(center.getX() - x), center.getZ() - z));
                        Npc npc = create(plugin, new Location(world, x, y, z, yaw, 0f), null, skin);
                        if (team > 0) {
                            plugin.teams().addNpc(team, npc.id());
                        }
                    }
                    ctx.send("npc.spawned-many", Messages.var("count", count));
                }).minArgs(1).complete(ctx -> {
                    if (ctx.size() == 1) {
                        return Completions.filter(List.of("10", "50", "100", "200"), ctx.last());
                    }
                    return flagSuggestions(ctx, List.of("skin=", "team="));
                });
    }

    private static Npc create(NpcWarsPlugin plugin, Location where, String label, String skin) throws CommandException {
        try {
            return plugin.npcs().create(where, label, skin);
        } catch (IllegalStateException ex) {
            throw new CommandException("npc.limit-reached", Messages.var("max", plugin.settings().maxNpcs));
        }
    }

    // ---------------------------------------------------------------- removing

    private static SubCommand remove() {
        return SubCommand.of("remove", "npcplugin.npc", "remove <id|ids|selected|look|team:<n>>",
                "Delete NPCs", ctx -> {
                    for (String arg : ctx.args()) {
                        if (arg.equalsIgnoreCase("all")) {
                            throw new CommandException("npc.use-removeall", Messages.var("label", ctx.label().replace(" remove", "")));
                        }
                    }
                    List<Npc> targets = Targets.manyOf(ctx, ctx.args());
                    for (Npc npc : targets) {
                        ctx.plugin().npcs().remove(npc);
                    }
                    ctx.send("npc.removed", Messages.var("count", targets.size()));
                }).aliases("delete").minArgs(1).complete(ctx -> Completions.filter(Targets.npcSuggestions(ctx), ctx.last()));
    }

    private static SubCommand removeAll() {
        return SubCommand.of("removeall", "npcplugin.npc", "removeall confirm", "Delete every NPC", ctx -> {
            if (ctx.size() == 0 || !ctx.arg(0).equalsIgnoreCase("confirm")) {
                throw new CommandException("npc.removeall-confirm", Messages.var("count", ctx.plugin().npcs().count()),
                        Messages.var("label", ctx.label().replace(" removeall", "")));
            }
            int removed = ctx.plugin().npcs().removeAll();
            ctx.send("npc.removed", Messages.var("count", removed));
        }).complete(ctx -> Completions.filter(List.of("confirm"), ctx.last()));
    }

    // ---------------------------------------------------------------- listing

    private static SubCommand list() {
        return SubCommand.of("list", "npcplugin.use", "list [page]", "List all NPCs", ctx -> {
            List<Npc> all = new ArrayList<>(ctx.plugin().npcs().all());
            int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int page = ctx.size() > 0 ? parseInt(ctx.arg(0), 1, pages, "general.invalid-page", Messages.var("pages", pages)) : 1;
            ctx.send("npc.list-header", Messages.var("count", all.size()), Messages.var("page", page), Messages.var("pages", pages));
            for (int i = (page - 1) * PAGE_SIZE; i < Math.min(all.size(), page * PAGE_SIZE); i++) {
                Npc npc = all.get(i);
                int team = ctx.plugin().teams().teamOfNpc(npc.id());
                ctx.send("npc.list-entry", Messages.var("id", npc.id()), Messages.var("label", labelOf(npc)),
                        Messages.var("team", team == 0 ? "-" : Integer.toString(team)),
                        Messages.var("state", stateOf(npc)),
                        Messages.var("location", locationText(npc)));
            }
        });
    }

    private static SubCommand info() {
        return SubCommand.of("info", "npcplugin.use", "info <id|look>", "Show details about an NPC", ctx -> {
            Npc npc = Targets.single(ctx, ctx.arg(0));
            int team = ctx.plugin().teams().teamOfNpc(npc.id());
            String teamText = "-";
            if (team != 0) {
                String name = ctx.plugin().teams().find(team).map(t -> t.adminName()).orElse(null);
                teamText = name == null ? Integer.toString(team) : team + " (" + name + ")";
            }
            ctx.send("npc.info", Messages.var("id", npc.id()), Messages.var("label", labelOf(npc)),
                    Messages.var("team", teamText), Messages.var("state", stateOf(npc)),
                    Messages.var("location", locationText(npc)),
                    Messages.var("skin", npc.skin() == null ? "default" : npc.skin()),
                    Messages.var("equipment", npc.equipmentView().size()));
        }).minArgs(1).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().npcs().idStrings(), ctx.last()) : List.of());
    }

    // ---------------------------------------------------------------- moving

    private static SubCommand teleportTo() {
        return SubCommand.of("tp", "npcplugin.npc", "tp <id|look>", "Teleport yourself to an NPC", ctx -> {
            Player player = ctx.player();
            Npc npc = Targets.single(ctx, ctx.arg(0));
            Location where = npc.currentLocation();
            if (where == null) {
                throw new CommandException("npc.not-spawned", Messages.var("id", npc.id()));
            }
            player.teleport(where);
            ctx.send("npc.teleported", Messages.var("id", npc.id()));
        }).aliases("goto").minArgs(1).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().npcs().idStrings(), ctx.last()) : List.of());
    }

    private static SubCommand teleportHere() {
        return SubCommand.of("tphere", "npcplugin.npc", "tphere <id|ids|selected|all|team:<n>>",
                "Bring NPCs to where you stand", ctx -> {
                    Player player = ctx.player();
                    List<Npc> targets = Targets.manyOf(ctx, ctx.args());
                    Location center = player.getLocation();
                    double radius = targets.size() == 1 ? 0 : Math.max(1.5, Math.sqrt(targets.size()) * 0.9);
                    for (int i = 0; i < targets.size(); i++) {
                        double distance = radius * Math.sqrt((i + 0.5) / targets.size());
                        double angle = i * GOLDEN_ANGLE;
                        Location spot = center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                        ctx.plugin().npcs().teleport(targets.get(i), spot);
                    }
                    ctx.send("npc.brought", Messages.var("count", targets.size()));
                }).minArgs(1).complete(ctx -> Completions.filter(Targets.npcSuggestions(ctx), ctx.last()));
    }

    // ---------------------------------------------------------------- selection

    private static SubCommand select() {
        return SubCommand.of("select", "npcplugin.npc", "select [add] <ids|all|look|team:<n>> | select clear | select list",
                "Select NPCs for /kitall and other commands", ctx -> {
                    Player player = ctx.player();
                    var selection = ctx.plugin().npcs().selection();
                    String first = ctx.arg(0).toLowerCase(Locale.ROOT);
                    switch (first) {
                        case "clear" -> {
                            selection.clear(player.getUniqueId());
                            ctx.send("npc.selection-cleared");
                        }
                        case "list" -> {
                            Set<Integer> ids = selection.get(player.getUniqueId());
                            ctx.send("npc.selection-list", Messages.var("count", ids.size()),
                                    Messages.var("ids", ids.isEmpty() ? "-" : ids.toString()));
                        }
                        default -> {
                            boolean add = first.equals("add");
                            List<String> words = add ? ctx.args().subList(1, ctx.size()) : ctx.args();
                            if (words.isEmpty()) {
                                throw new CommandException("general.usage", Messages.var("usage", "/" + ctx.label() + " select [add] <targets>"));
                            }
                            Set<Integer> ids = new LinkedHashSet<>();
                            if (add) {
                                ids.addAll(selection.get(player.getUniqueId()));
                            }
                            for (Npc npc : Targets.manyOf(ctx, words)) {
                                ids.add(npc.id());
                            }
                            selection.set(player.getUniqueId(), ids);
                            ctx.send("npc.selected", Messages.var("count", ids.size()));
                        }
                    }
                }).aliases("sel").minArgs(1).complete(ctx -> {
                    List<String> options = new ArrayList<>(Targets.npcSuggestions(ctx));
                    options.addAll(List.of("add", "clear", "list"));
                    return Completions.filter(options, ctx.last());
                });
    }

    // ---------------------------------------------------------------- styling

    private static SubCommand skin() {
        return SubCommand.of("skin", "npcplugin.npc", "skin <id|ids|selected|all> <player|reset>",
                "Give NPCs a player's skin", ctx -> {
                    String value = ctx.arg(ctx.size() - 1);
                    boolean reset = value.equalsIgnoreCase("reset") || value.equalsIgnoreCase("default");
                    String skin = reset ? null : skinOrNull(value);
                    if (!reset && skin == null) {
                        throw new CommandException("npc.invalid-skin", Messages.var("input", value));
                    }
                    List<Npc> targets = Targets.manyOf(ctx, ctx.args().subList(0, ctx.size() - 1));
                    for (Npc npc : targets) {
                        ctx.plugin().npcs().setSkin(npc, skin);
                    }
                    ctx.send("npc.skin-set", Messages.var("count", targets.size()), Messages.var("skin", skin == null ? "default" : skin));
                }).minArgs(2).complete(ctx -> {
                    List<String> options = new ArrayList<>(Targets.npcSuggestions(ctx));
                    Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                    options.add("reset");
                    return Completions.filter(options, ctx.last());
                });
    }

    private static SubCommand rename() {
        return SubCommand.of("rename", "npcplugin.npc", "rename <id> <label...>",
                "Change an NPC's label (admin label; shown above it only if npc.show-nametag is on)", ctx -> {
                    Npc npc = Targets.single(ctx, ctx.arg(0));
                    String label = cleanLabel(String.join(" ", ctx.args().subList(1, ctx.size())));
                    ctx.plugin().npcs().setLabel(npc, label == null ? "NPC " + npc.id() : label);
                    ctx.send("npc.renamed", Messages.var("id", npc.id()), Messages.var("label", npc.label()));
                }).minArgs(2).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().npcs().idStrings(), ctx.last()) : List.of());
    }

    private static SubCommand equip() {
        return SubCommand.of("equip", "npcplugin.equip", "equip <id|look>",
                "Open the equipment GUI (same as Shift + Right-click)", ctx -> {
                    Player player = ctx.player();
                    Npc npc = Targets.single(ctx, ctx.arg(0));
                    new EquipmentGui(ctx.plugin(), npc).open(player);
                }).minArgs(1).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().npcs().idStrings(), ctx.last()) : List.of());
    }

    private static SubCommand heal() {
        return SubCommand.of("heal", "npcplugin.npc", "heal <id|ids|selected|all|team:<n>>", "Restore NPC health", ctx -> {
            List<Npc> targets = Targets.manyOf(ctx, ctx.args());
            targets.forEach(npc -> ctx.plugin().npcs().heal(npc));
            ctx.send("npc.healed-many", Messages.var("count", targets.size()));
        }).minArgs(1).complete(ctx -> Completions.filter(Targets.npcSuggestions(ctx), ctx.last()));
    }

    // ---------------------------------------------------------------- helpers

    static String labelOf(Npc npc) {
        return npc.label() == null || npc.label().isBlank() ? "NPC " + npc.id() : npc.label();
    }

    private static String stateOf(Npc npc) {
        if (npc.isLive()) {
            return "alive";
        }
        return npc.isSuppressed() ? "down" : "unspawned";
    }

    private static String locationText(Npc npc) {
        Location where = npc.currentLocation();
        if (where == null) {
            return npc.worldName() + " (world not loaded)";
        }
        return String.format(Locale.ROOT, "%s %.0f %.0f %.0f", where.getWorld().getName(), where.getX(), where.getY(), where.getZ());
    }

    private static String cleanLabel(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_LABEL_LENGTH ? trimmed.substring(0, MAX_LABEL_LENGTH) : trimmed;
    }

    private static String skinOrNull(String text) {
        return text != null && SKIN_NAME.matcher(text).matches() ? text : null;
    }

    private static int parseInt(String text, int min, int max, String errorKey,
                                net.kyori.adventure.text.minimessage.tag.resolver.TagResolver extra) throws CommandException {
        try {
            int value = Integer.parseInt(text);
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new CommandException(errorKey, Messages.var("input", text), extra);
    }

    static List<String> flagSuggestions(CommandContext ctx, List<String> flags) {
        String last = ctx.last();
        List<String> options = new ArrayList<>(flags);
        if (last.toLowerCase(Locale.ROOT).startsWith("skin=")) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add("skin=" + p.getName()));
            return Completions.filter(names, last);
        }
        if (last.toLowerCase(Locale.ROOT).startsWith("team=")) {
            List<String> teams = new ArrayList<>();
            ctx.plugin().teams().teams().forEach(t -> teams.add("team=" + t.id()));
            return Completions.filter(teams, last);
        }
        return Completions.filter(options, last);
    }
}
