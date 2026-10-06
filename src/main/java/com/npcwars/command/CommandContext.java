package com.npcwars.command;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Messages;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Everything a sub-command needs: who ran it, the arguments after the sub-command name, and shortcuts for the common
 * argument checks. {@link #shift()} produces the context for a nested sub-command.
 */
public final class CommandContext {

    private final NpcWarsPlugin plugin;
    private final CommandSender sender;
    private final String label;
    private final List<String> args;

    public CommandContext(NpcWarsPlugin plugin, CommandSender sender, String label, List<String> args) {
        this.plugin = plugin;
        this.sender = sender;
        this.label = label;
        this.args = args;
    }

    public NpcWarsPlugin plugin() {
        return plugin;
    }

    public CommandSender sender() {
        return sender;
    }

    /** The command label plus the sub-commands already consumed, e.g. {@code npc team}. */
    public String label() {
        return label;
    }

    public List<String> args() {
        return args;
    }

    public int size() {
        return args.size();
    }

    public String arg(int index) {
        return args.get(index);
    }

    /** @return the argument being typed during tab completion (empty if there is none yet) */
    public String last() {
        return args.isEmpty() ? "" : args.get(args.size() - 1);
    }

    /** @return the sender as a player, or throws a "players only" error */
    public Player player() throws CommandException {
        if (sender instanceof Player player) {
            return player;
        }
        throw new CommandException("general.players-only");
    }

    /** @return a context for a nested sub-command: the first argument is consumed and added to the label */
    public CommandContext shift() {
        return new CommandContext(plugin, sender, label + " " + args.get(0), args.subList(1, args.size()));
    }

    public void send(String key, TagResolver... resolvers) {
        plugin.messages().send(sender, key, resolvers);
    }

    /**
     * Parses a positive team number.
     *
     * @throws CommandException if the text is not a whole number of at least 1
     */
    public int teamNumber(String text) throws CommandException {
        try {
            int number = Integer.parseInt(text);
            if (number > 0) {
                return number;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new CommandException("team.invalid-number", Messages.var("input", text));
    }
}
