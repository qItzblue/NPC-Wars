package com.npcwars.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Small helpers for building tab-completion lists. */
public final class Completions {

    private Completions() {
    }

    /** Returns the candidates that start with {@code prefix} (case-insensitive), in their original order. */
    public static List<String> filter(Collection<String> candidates, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(candidate);
            }
        }
        return out;
    }

    /** Convenience overload for varargs candidates. */
    public static List<String> filter(String prefix, String... candidates) {
        return filter(List.of(candidates), prefix);
    }
}
