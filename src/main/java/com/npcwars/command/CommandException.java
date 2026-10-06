package com.npcwars.command;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Thrown inside a command to abort with a configurable error message (a key from the {@code messages} section). */
public final class CommandException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String messageKey;
    private final transient TagResolver[] resolvers;

    public CommandException(String messageKey, TagResolver... resolvers) {
        super(messageKey, null, false, false);
        this.messageKey = messageKey;
        this.resolvers = resolvers;
    }

    public String messageKey() {
        return messageKey;
    }

    public TagResolver[] resolvers() {
        return resolvers;
    }
}
