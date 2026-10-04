package com.npcwars.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Splits command arguments into positional words and {@code key=value} flags (for example {@code skin=Notch},
 * {@code team=3}, {@code for=30s}). Only the listed keys are treated as flags, so a plain word that happens to contain
 * an equals sign is not swallowed.
 */
public final class ArgFlags {

    private final List<String> positional = new ArrayList<>();
    private final Map<String, String> flags = new LinkedHashMap<>();

    private ArgFlags() {
    }

    public static ArgFlags parse(List<String> args, Set<String> knownKeys) {
        ArgFlags result = new ArgFlags();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (eq > 0) {
                String key = arg.substring(0, eq).toLowerCase(Locale.ROOT);
                if (knownKeys.contains(key)) {
                    result.flags.put(key, arg.substring(eq + 1));
                    continue;
                }
            }
            result.positional.add(arg);
        }
        return result;
    }

    public List<String> positional() {
        return positional;
    }

    /** @return the flag's value, or {@code null} if absent */
    public String get(String key) {
        return flags.get(key);
    }

    public boolean has(String key) {
        return flags.containsKey(key);
    }
}
