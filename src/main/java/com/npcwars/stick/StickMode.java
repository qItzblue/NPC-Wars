package com.npcwars.stick;

import java.util.Locale;

/** What the dupe stick does when it is used. */
public enum StickMode {
    /** Every real player standing in the selected area is duplicated into an NPC, in place and facing the same way. */
    COPY_PLAYERS("Copy players in the area"),
    /** The area is filled with copies of the stick's user, all facing the way the user faces. */
    FILL_AREA("Fill the area with copies of you"),
    /** One copy of the user is placed where the user points, facing the way the user faces. */
    SINGLE("Place one copy of you");

    private final String description;

    StickMode(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public StickMode next() {
        StickMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static StickMode parse(String text, StickMode fallback) {
        if (text == null) {
            return fallback;
        }
        String key = text.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "copy", "copy_players", "players" -> COPY_PLAYERS;
            case "fill", "fill_area", "area" -> FILL_AREA;
            case "single", "one" -> SINGLE;
            default -> fallback;
        };
    }
}
