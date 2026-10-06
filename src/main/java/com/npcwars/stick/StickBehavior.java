package com.npcwars.stick;

import java.util.Locale;

/** What a duplicated NPC does once it exists. */
public enum StickBehavior {
    STAND("Stand still"),
    WALK_FORWARD("Walk forward");

    private final String description;

    StickBehavior(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public StickBehavior toggled() {
        return this == STAND ? WALK_FORWARD : STAND;
    }

    public static StickBehavior parse(String text, StickBehavior fallback) {
        if (text == null) {
            return fallback;
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "stand", "still", "idle" -> STAND;
            case "walk", "forward", "walk_forward" -> WALK_FORWARD;
            default -> fallback;
        };
    }
}
