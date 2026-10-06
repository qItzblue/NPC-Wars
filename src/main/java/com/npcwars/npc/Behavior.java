package com.npcwars.npc;

import java.util.Locale;

/** What an NPC does while nothing else (a fight, a mass action, a route) controls it. */
public enum Behavior {
    /** Stands where it is. */
    STILL,
    /** Lives like an SMP player: wanders near home, looks at players, fidgets, can be hurt in survival, and may chat. */
    LIFE;

    public static Behavior parse(String text, Behavior fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
