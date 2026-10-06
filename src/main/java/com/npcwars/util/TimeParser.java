package com.npcwars.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats human durations such as {@code 30s}, {@code 5m}, {@code 1h} or {@code 1h30m15s}.
 * A bare number is read as seconds.
 */
public final class TimeParser {

    private static final Pattern FULL = Pattern.compile("^(?:\\d+[dhms])+$|^\\d+$");
    private static final Pattern TOKEN = Pattern.compile("(\\d+)([dhms]?)");

    private TimeParser() {
    }

    /**
     * @param input text such as {@code 90}, {@code 30s}, {@code 5m}, {@code 1h30m}
     * @return the duration in whole seconds (never negative)
     * @throws IllegalArgumentException if the text is empty, malformed or too large
     */
    public static long parseSeconds(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("empty duration");
        }
        String text = input.trim().toLowerCase(Locale.ROOT);
        if (!FULL.matcher(text).matches()) {
            throw new IllegalArgumentException("invalid duration: " + input);
        }
        Matcher matcher = TOKEN.matcher(text);
        long total = 0;
        try {
            while (matcher.find()) {
                long amount = Long.parseLong(matcher.group(1));
                long unit = switch (matcher.group(2)) {
                    case "d" -> 86_400L;
                    case "h" -> 3_600L;
                    case "m" -> 60L;
                    default -> 1L;
                };
                total = Math.addExact(total, Math.multiplyExact(amount, unit));
            }
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("duration too large: " + input);
        }
        return total;
    }

    /** Formats seconds as e.g. {@code 1h 5m 3s}; zero becomes {@code 0s}. */
    public static String format(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        long secs = seconds % 60;
        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append("d ");
        }
        if (hours > 0) {
            out.append(hours).append("h ");
        }
        if (minutes > 0) {
            out.append(minutes).append("m ");
        }
        if (secs > 0) {
            out.append(secs).append("s ");
        }
        return out.toString().trim();
    }
}
