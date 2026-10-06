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

/** {@code /npcwars} sub-commands that create, find, move and style NPCs. */
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
                .register(heal())
                .register(give())
                .register(move())
                .register(cloneNpc());
    }

    // ---------------------------------------------------------------- spawning

    private static SubCommand spawn() {
        return SubCommand.of("spawn", "npcplugin.npc", "spawn [name] [skin=<player|random|none>] [team=<n>]",
                "Spawn an NPC where you stand (random name and skin unless you give them)", ctx -> {
                    Player player = ctx.player();
                    ArgFlags flags = ArgFlags.parse(ctx.args(), Set.of("skin", "team"));
                    String label = cleanLabel(String.join(" ", flags.positional()));
                    if (label == null && ctx.plugin().settings().randomOnSpawn) {
                        label = ctx.plugin().pools().randomName();
                    }
                    String skin = pickSkin(ctx.plugin(), flags.get("skin"));
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
                    String skinFlag = flags.get("skin");
                    pickSkin(ctx.plugin(), skinFlag); // validates the flag before anything is spawned
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
                        String label = plugin.settings().randomOnSpawn ? plugin.pools().randomName() : null;
                        Npc npc = create(plugin, new Location(world, x, y, z, yaw, 0f), label, pickSkin(plugin, skinFlag));
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
        return SubCommand.of("skin", "npcplugin.npc", "skin <id|ids|selected|all> <player|random|reset>",
                "Give NPCs a player's skin (random = a different one from the pool for each)", ctx -> {
                    String value = ctx.arg(ctx.size() - 1);
                    boolean random = value.equalsIgnoreCase("random");
                    boolean reset = value.equalsIgnoreCase("reset") || value.equalsIgnoreCase("default");
                    String fixed = reset || random ? null : skinOrNull(value);
                    if (!reset && !random && fixed == null) {
                        throw new CommandException("npc.invalid-skin", Messages.var("input", value));
                    }
                    List<Npc> targets = Targets.manyOf(ctx, ctx.args().subList(0, ctx.size() - 1));
                    for (Npc npc : targets) {
                        ctx.plugin().npcs().setSkin(npc, random ? ctx.plugin().pools().randomSkin() : fixed);
                    }
                    ctx.send("npc.skin-set", Messages.var("count", targets.size()),
                            Messages.var("skin", random ? "random" : fixed == null ? "default" : fixed));
                }).minArgs(2).complete(ctx -> {
                    List<String> options = new ArrayList<>(Targets.npcSuggestions(ctx));
                    Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                    options.add("reset");
                    options.add("random");
                    return Completions.filter(options, ctx.last());
                });
    }

    private static SubCommand rename() {
        return SubCommand.of("rename", "npcplugin.npc", "rename <id> <name...|random>",
                "Change an NPC's name (shown above it when npc.show-nametag is on)", ctx -> {
                    Npc npc = Targets.single(ctx, ctx.arg(0));
                    String typed = String.join(" ", ctx.args().subList(1, ctx.size()));
                    String label = typed.equalsIgnoreCase("random") ? ctx.plugin().pools().randomName() : cleanLabel(typed);
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

    private static SubCommand cloneNpc() {
        return SubCommand.of("clone", "npcplugin.npc", "clone <id> [count]",
                "Duplicate an NPC (name, skin, whole inventory, team) around it", ctx -> {
                    Npc source = Targets.single(ctx, ctx.arg(0));
                    int count = ctx.size() > 1 ? parseInt(ctx.arg(1), 1, 100, "npc.invalid-count", Messages.var("max", 100)) : 1;
                    Location origin = source.currentLocation();
                    if (origin == null) {
                        throw new CommandException("npc.not-spawned", Messages.var("id", source.id()));
                    }
                    int made = 0;
                    for (int i = 0; i < count; i++) {
                        double angle = i * GOLDEN_ANGLE;
                        double distance = 1.5 + Math.sqrt(i) * 1.2;
                        Location at = origin.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                        try {
                            ctx.plugin().npcs().duplicate(source, at);
                            made++;
                        } catch (IllegalStateException ex) {
                            throw new CommandException("npc.limit-reached", Messages.var("max", ctx.plugin().settings().maxNpcs));
                        }
                    }
                    ctx.send("npc.cloned", Messages.var("count", made), Messages.var("id", source.id()));
                }).minArgs(1).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().npcs().idStrings(), ctx.last()) : List.of());
    }

    private static SubCommand move() {
        return SubCommand.of("move", "npcplugin.npc", "move <id|ids|selected|all|team:<n>> <x> <y> <z> [world]",
                "Teleport NPCs to coordinates and make that their new home (works from the console)", ctx -> {
                    List<Npc> targets = Targets.manyOf(ctx, List.of(ctx.arg(0)));
                    double[] xyz = new double[3];
                    for (int i = 0; i < 3; i++) {
                        try {
                            xyz[i] = Double.parseDouble(ctx.arg(i + 1));
                            if (!Double.isFinite(xyz[i])) {
                                throw new NumberFormatException();
                            }
                        } catch (NumberFormatException ex) {
                            throw new CommandException("route.invalid-coordinate", Messages.var("input", ctx.arg(i + 1)));
                        }
                    }
                    World world = ctx.size() > 4 ? Bukkit.getWorld(ctx.arg(4))
                            : ctx.sender() instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
                    if (world == null) {
                        throw new CommandException("route.world-unknown", Messages.var("input", ctx.arg(4)));
                    }
                    for (Npc npc : targets) {
                        ctx.plugin().npcs().teleport(npc, new Location(world, xyz[0], xyz[1], xyz[2], npc.yaw(), npc.pitch()));
                    }
                    ctx.send("npc.moved", Messages.var("count", targets.size()));
                }).minArgs(4).complete(ctx -> ctx.size() == 1 ? Completions.filter(Targets.npcSuggestions(ctx), ctx.last()) : List.of());
    }

    private static SubCommand give() {
        return SubCommand.of("give", "npcplugin.npc", "give <id|ids|selected|all|team:<n>> <ITEM[ amount][ enchant:level]>...",
                "Put items into NPC inventories (they use them in fights); \"give <targets> clear\" empties them", ctx -> {
                    // The item is everything after the target token, e.g. "give 3 netherite_sword sharpness:3" or
                    // "give all wind_charge 16".
                    List<Npc> targets = Targets.manyOf(ctx, List.of(ctx.arg(0)));
                    if (ctx.size() == 2 && ctx.arg(1).equalsIgnoreCase("clear")) {
                        for (Npc npc : targets) {
                            npc.clearEquipment();
                            ctx.plugin().npcs().applyInventory(npc);
                        }
                        ctx.plugin().data().requestSave();
                        ctx.send("npc.cleared", Messages.var("count", targets.size()));
                        return;
                    }
                    String line = String.join(" ", ctx.args().subList(1, ctx.size()));
                    org.bukkit.inventory.ItemStack item;
                    try {
                        item = com.npcwars.kit.ItemParser.parse(line);
                    } catch (IllegalArgumentException ex) {
                        throw new CommandException("npc.invalid-item", Messages.var("input", line), Messages.var("reason", ex.getMessage()));
                    }
                    int full = 0;
                    for (Npc npc : targets) {
                        if (!ctx.plugin().npcs().giveItem(npc, item)) {
                            full++;
                        }
                    }
                    ctx.send("npc.gave", Messages.var("count", targets.size() - full), Messages.var("full", full));
                }).minArgs(2).complete(ctx -> {
                    if (ctx.size() == 1) {
                        return Completions.filter(Targets.npcSuggestions(ctx), ctx.last());
                    }
                    if (ctx.size() == 2) {
                        List<String> names = new ArrayList<>(List.of("clear"));
                        for (org.bukkit.Material material : org.bukkit.Material.values()) {
                            if (material.isItem() && !material.name().startsWith("LEGACY_")) {
                                names.add(material.name().toLowerCase(Locale.ROOT));
                            }
                        }
                        return Completions.filter(names, ctx.last());
                    }
                    return List.of();
                });
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

    /**
     * Resolves a {@code skin=} flag: a player name, {@code random} (from the pool), {@code none} (the default skin) or,
     * when the flag is absent, a random one if {@code appearance.random-on-spawn} is on.
     *
     * @throws CommandException if the flag is not a valid player name
     */
    static String pickSkin(NpcWarsPlugin plugin, String flag) throws CommandException {
        if (flag == null) {
            return plugin.settings().randomOnSpawn ? plugin.pools().randomSkin() : null;
        }
        if (flag.equalsIgnoreCase("random")) {
            return plugin.pools().randomSkin();
        }
        if (flag.equalsIgnoreCase("none") || flag.equalsIgnoreCase("default")) {
            return null;
        }
        String skin = skinOrNull(flag);
        if (skin == null) {
            throw new CommandException("npc.invalid-skin", Messages.var("input", flag));
        }
        return skin;
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
