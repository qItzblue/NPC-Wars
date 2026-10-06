package com.npcwars.command;

import com.npcwars.config.Messages;
import com.npcwars.util.Completions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.CommandSender;

/**
 * Dispatches the first argument to a registered {@link SubCommand}, with permission checks, usage errors, help text
 * and tab completion that only offers what the sender may actually use.
 */
public final class SubCommandRouter {

    private static final int HELP_PAGE_SIZE = 8;

    private final Map<String, SubCommand> byName = new LinkedHashMap<>();
    private final Map<String, SubCommand> lookup = new LinkedHashMap<>();

    public SubCommandRouter register(SubCommand command) {
        byName.put(command.name(), command);
        lookup.put(command.name().toLowerCase(Locale.ROOT), command);
        for (String alias : command.aliases()) {
            lookup.put(alias.toLowerCase(Locale.ROOT), command);
        }
        return this;
    }

    /**
     * Runs the sub-command named by the first argument. Shows help when there is none, and reports errors to the
     * sender instead of throwing.
     */
    public void dispatch(CommandContext ctx) {
        try {
            if (ctx.size() == 0) {
                help(ctx, 1);
                return;
            }
            String first = ctx.arg(0).toLowerCase(Locale.ROOT);
            if (first.equals("help") || first.equals("?")) {
                int page = 1;
                if (ctx.size() > 1 && !ctx.arg(1).isEmpty() && ctx.arg(1).length() <= 6
                        && ctx.arg(1).chars().allMatch(Character::isDigit)) {
                    page = Integer.parseInt(ctx.arg(1));
                }
                help(ctx, page);
                return;
            }
            SubCommand command = lookup.get(first);
            if (command == null) {
                throw new CommandException("general.unknown-subcommand", Messages.var("input", ctx.arg(0)),
                        Messages.var("label", ctx.label()));
            }
            if (!ctx.sender().hasPermission(command.permission())) {
                throw new CommandException("general.no-permission");
            }
            CommandContext inner = ctx.shift();
            if (inner.size() < command.minArgs()) {
                throw new CommandException("general.usage", Messages.var("usage", "/" + ctx.label() + " " + command.usage()));
            }
            command.executor().run(inner);
        } catch (CommandException ex) {
            ctx.plugin().messages().sendAlways(ctx.sender(), ex.messageKey(), ex.resolvers());
        }
    }

    /** Completes the sub-command name, or delegates to the sub-command's own completer. */
    public List<String> complete(CommandContext ctx) {
        if (ctx.size() <= 1) {
            List<String> names = new ArrayList<>();
            for (SubCommand command : byName.values()) {
                if (ctx.sender().hasPermission(command.permission())) {
                    names.add(command.name());
                }
            }
            names.add("help");
            return Completions.filter(names, ctx.last());
        }
        SubCommand command = lookup.get(ctx.arg(0).toLowerCase(Locale.ROOT));
        if (command == null || !ctx.sender().hasPermission(command.permission())) {
            return List.of();
        }
        try {
            return command.completer().complete(ctx.shift());
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private void help(CommandContext ctx, int requestedPage) {
        CommandSender sender = ctx.sender();
        List<SubCommand> usable = new ArrayList<>();
        for (SubCommand command : byName.values()) {
            if (sender.hasPermission(command.permission())) {
                usable.add(command);
            }
        }
        int pages = Math.max(1, (usable.size() + HELP_PAGE_SIZE - 1) / HELP_PAGE_SIZE);
        int page = Math.max(1, Math.min(pages, requestedPage));
        ctx.send("help.header", Messages.var("label", ctx.label()), Messages.var("page", page), Messages.var("pages", pages));
        for (int i = (page - 1) * HELP_PAGE_SIZE; i < Math.min(usable.size(), page * HELP_PAGE_SIZE); i++) {
            SubCommand command = usable.get(i);
            ctx.send("help.entry", Messages.var("usage", "/" + ctx.label() + " " + command.usage()),
                    Messages.var("description", command.description()));
        }
    }
}
