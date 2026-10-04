package com.npcwars.command;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Messages;
import com.npcwars.gui.KitMenu;
import com.npcwars.gui.KitScope;
import com.npcwars.team.Team;
import com.npcwars.util.Completions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * {@code /kitall [all|selected|team <n>|npc <id>]}: opens the combined kit menu. Without an argument the kit goes to
 * the NPCs the player has selected, or to every NPC if nothing is selected.
 */
public final class KitAllCommand implements TabExecutor {

    private final NpcWarsPlugin plugin;

    public KitAllCommand(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only");
            return true;
        }
        if (!player.hasPermission("npcplugin.kit")) {
            messages.send(player, "general.no-permission");
            return true;
        }
        KitScope scope;
        try {
            scope = parseScope(player, args);
        } catch (CommandException ex) {
            messages.send(player, ex.messageKey(), ex.resolvers());
            return true;
        }
        if (scope.resolve(plugin, player).isEmpty()) {
            messages.send(player, "kits.no-targets", Messages.var("scope", scope.describe()));
            return true;
        }
        if (plugin.kits().allKits().isEmpty()) {
            messages.send(player, "kits.none");
            return true;
        }
        new KitMenu(plugin, player, scope).open(player);
        return true;
    }

    private KitScope parseScope(Player player, String[] args) throws CommandException {
        if (args.length == 0) {
            return plugin.npcs().selection().get(player.getUniqueId()).isEmpty() ? KitScope.all() : KitScope.selected();
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "all":
                return KitScope.all();
            case "selected":
                return KitScope.selected();
            case "team":
                if (args.length < 2) {
                    throw new CommandException("general.usage", Messages.var("usage", "/kitall team <number>"));
                }
                return KitScope.team(parsePositive(args[1], "team.invalid-number"));
            case "npc":
                if (args.length < 2) {
                    throw new CommandException("general.usage", Messages.var("usage", "/kitall npc <id>"));
                }
                return KitScope.single(parsePositive(args[1], "npc.invalid-target"));
            default:
                throw new CommandException("general.usage", Messages.var("usage", "/kitall [all|selected|team <n>|npc <id>]"));
        }
    }

    private static int parsePositive(String text, String errorKey) throws CommandException {
        try {
            int value = Integer.parseInt(text);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new CommandException(errorKey, Messages.var("input", text));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("npcplugin.kit")) {
            return List.of();
        }
        if (args.length == 1) {
            return Completions.filter(List.of("all", "selected", "team", "npc"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("team")) {
            List<String> teams = new ArrayList<>();
            for (Team team : plugin.teams().teams()) {
                teams.add(Integer.toString(team.id()));
            }
            return Completions.filter(teams, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return Completions.filter(plugin.npcs().idStrings(), args[1]);
        }
        return List.of();
    }
}
