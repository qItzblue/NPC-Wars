package com.npcwars.action;

import java.util.List;

/**
 * Something every NPC can be told to do with {@code /massaction}. Implement this interface and pass it to
 * {@link ActionRegistry#register(NpcAction)} (reachable through {@code NpcWarsPlugin#actions()}); the command, its
 * help text and tab-completion pick the new action up automatically.
 */
public interface NpcAction {

    /** Lower-case canonical name, as typed in the command (e.g. {@code walk}). */
    String name();

    /** Alternative names that also select this action. */
    default List<String> aliases() {
        return List.of();
    }

    /** One-line description shown by {@code /massaction} help. */
    String description();

    /** Argument hint shown in help, e.g. {@code [direction] [blocks]}; empty if the action takes none. */
    default String usage() {
        return "";
    }

    /**
     * Parses the arguments once for the whole command run. The returned object is then started on every selected NPC,
     * so per-NPC state belongs in a map inside it (keyed by NPC id) or in {@link PreparedAction#start}.
     *
     * @param args everything typed after the action name, except the common {@code for=}, {@code team=} and
     *             {@code npc=} flags
     * @throws ActionException if the arguments are invalid; its message key is shown to the sender
     */
    PreparedAction prepare(ActionContext context, List<String> args) throws ActionException;

    /**
     * Suggestions for the last element of {@code args} (the argument currently being typed, possibly empty).
     */
    default List<String> complete(ActionContext context, List<String> args) {
        return List.of();
    }
}
