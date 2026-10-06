package com.npcwars.stick;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** The dupe stick could not do what was asked; carries the message key to show the player. */
public final class StickException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String messageKey;
    private final transient TagResolver[] resolvers;
    private final int partial;

    public StickException(String messageKey, TagResolver... resolvers) {
        this(messageKey, resolvers, 0);
    }

    public StickException(String messageKey, TagResolver resolver, int partial) {
        this(messageKey, new TagResolver[] {resolver}, partial);
    }

    private StickException(String messageKey, TagResolver[] resolvers, int partial) {
        super(messageKey, null, false, false);
        this.messageKey = messageKey;
        this.resolvers = resolvers;
        this.partial = partial;
    }

    public String messageKey() {
        return messageKey;
    }

    public TagResolver[] resolvers() {
        return resolvers;
    }

    /** How many NPCs were already made before the problem came up. */
    public int partial() {
        return partial;
    }
}
