package com.npcwars.chat;

import java.util.regex.Pattern;

/** Cleans model output for in-game chat and spots when a player talks about an NPC. Pure string work. */
public final class ChatText {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n]]|§.");
    private static final Pattern MARKDOWN = Pattern.compile("[*_`~#>]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private ChatText() {
    }

    /**
     * Turns a reply into one plain chat line: no newlines, colour codes or markdown, no surrounding quotes, and at most
     * {@code maxChars} characters (cut at a sentence or word end where possible).
     */
    public static String clean(String raw, int maxChars) {
        if (raw == null) {
            return "";
        }
        String text = CONTROL.matcher(raw).replaceAll("");
        text = MARKDOWN.matcher(text).replaceAll("");
        text = SPACES.matcher(text).replaceAll(" ").strip();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1).strip();
        }
        int limit = Math.max(10, maxChars);
        if (text.length() > limit) {
            String cut = text.substring(0, limit);
            int sentence = Math.max(cut.lastIndexOf(". "), Math.max(cut.lastIndexOf("! "), cut.lastIndexOf("? ")));
            if (sentence > limit / 2) {
                cut = cut.substring(0, sentence + 1);
            } else {
                int space = cut.lastIndexOf(' ');
                if (space > limit / 2) {
                    cut = cut.substring(0, space);
                }
            }
            text = cut.strip();
        }
        return text;
    }

    /** @return {@code true} if the message contains the name as a whole word, ignoring case */
    public static boolean mentions(String name, String message) {
        if (name == null || name.isBlank() || message == null) {
            return false;
        }
        return Pattern.compile("(?i)(?<![\\w])" + Pattern.quote(name.strip()) + "(?![\\w])").matcher(message).find();
    }
}
