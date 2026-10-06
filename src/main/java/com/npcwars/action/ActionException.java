package com.npcwars.action;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Thrown by {@link NpcAction#prepare} for invalid arguments; carries a message key and its placeholders. */
public final class ActionException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String messageKey;
    private final transient TagResolver[] resolvers;

    public ActionException(String messageKey, TagResolver... resolvers) {
        super(messageKey);
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
