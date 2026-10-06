package com.npcwars.command;

import java.util.List;

/**
 * One sub-command of {@code /npcwars} (or of a nested group such as {@code /npcwars team}). Instances are built with the
 * fluent {@link #of} factory so each command stays a few lines of lambdas.
 */
public final class SubCommand {

    /** The command body. */
    @FunctionalInterface
    public interface Executor {
        void run(CommandContext ctx) throws CommandException;
    }

    /** Tab completion for the arguments after the sub-command name. */
    @FunctionalInterface
    public interface Completer {
        List<String> complete(CommandContext ctx);
    }

    private final String name;
    private final String permission;
    private final String usage;
    private final String description;
    private final Executor executor;
    private List<String> aliases = List.of();
    private Completer completer = ctx -> List.of();
    private int minArgs;

    private SubCommand(String name, String permission, String usage, String description, Executor executor) {
        this.name = name;
        this.permission = permission;
        this.usage = usage;
        this.description = description;
        this.executor = executor;
    }

    /**
     * @param usage the text after the command label, e.g. {@code spawn [label] [skin=<name>]}
     */
    public static SubCommand of(String name, String permission, String usage, String description, Executor executor) {
        return new SubCommand(name, permission, usage, description, executor);
    }

    public SubCommand aliases(String... aliases) {
        this.aliases = List.of(aliases);
        return this;
    }

    public SubCommand complete(Completer completer) {
        this.completer = completer;
        return this;
    }

    /** Minimum number of arguments; fewer shows the usage line instead of running the command. */
    public SubCommand minArgs(int minArgs) {
        this.minArgs = minArgs;
        return this;
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    public String permission() {
        return permission;
    }

    public String usage() {
        return usage;
    }

    public String description() {
        return description;
    }

    public int minArgs() {
        return minArgs;
    }

    Executor executor() {
        return executor;
    }

    Completer completer() {
        return completer;
    }
}
