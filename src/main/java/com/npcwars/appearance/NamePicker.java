package com.npcwars.appearance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Picks random names without handing out one that is already taken. Pure logic. */
public final class NamePicker {

    private static final int MAX_LENGTH = 16;

    private NamePicker() {
    }

    /**
     * @param pool  candidate names
     * @param taken names already in use (compared ignoring case)
     * @return an unused name from the pool; if the pool is exhausted, a pool name with a number appended; if the pool is
     *         empty, {@code "Player"} plus a number
     */
    public static String unique(List<String> pool, Set<String> taken, RandomGenerator random) {
        Set<String> used = lower(taken);
        List<String> free = new ArrayList<>();
        for (String name : pool) {
            if (!used.contains(name.toLowerCase(Locale.ROOT))) {
                free.add(name);
            }
        }
        if (!free.isEmpty()) {
            return free.get(random.nextInt(free.size()));
        }
        String base = pool.isEmpty() ? "Player" : pool.get(random.nextInt(pool.size()));
        for (int n = 2; n < 10_000; n++) {
            String suffix = Integer.toString(n);
            String trimmed = base.length() + suffix.length() > MAX_LENGTH
                    ? base.substring(0, MAX_LENGTH - suffix.length()) : base;
            String candidate = trimmed + suffix;
            if (!used.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return base + random.nextInt(1_000_000);
    }

    /** @return a random entry, or {@code null} for an empty pool */
    public static String any(List<String> pool, RandomGenerator random) {
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    private static Set<String> lower(Set<String> names) {
        Set<String> out = new java.util.HashSet<>();
        for (String name : names) {
            if (name != null) {
                out.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }
}
