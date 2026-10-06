package com.npcwars.route;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** The saved routes, keyed by name (case-insensitive). Pure Java; storage is {@link RouteStorage}. */
public final class RouteManager {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,24}");

    private final Map<String, Route> routes = new LinkedHashMap<>();
    private Runnable changeListener = () -> { };

    public static boolean validName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener == null ? () -> { } : listener;
    }

    /** @return the route, or {@code null} */
    public Route get(String name) {
        return name == null ? null : routes.get(name.toLowerCase(Locale.ROOT));
    }

    /** @return the new route, or {@code null} if the name is invalid or taken */
    public Route create(String name, String worldName) {
        if (!validName(name) || routes.containsKey(name.toLowerCase(Locale.ROOT))) {
            return null;
        }
        Route route = new Route(name, worldName);
        routes.put(name.toLowerCase(Locale.ROOT), route);
        changeListener.run();
        return route;
    }

    public boolean delete(String name) {
        boolean removed = name != null && routes.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            changeListener.run();
        }
        return removed;
    }

    /** Call after changing a route's points so the data file is saved. */
    public void changed() {
        changeListener.run();
    }

    public List<Route> all() {
        return new ArrayList<>(routes.values());
    }

    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (Route route : routes.values()) {
            names.add(route.name());
        }
        return names;
    }

    /** Replaces everything (used when loading). Does not notify the change listener. */
    public void restore(Route route) {
        routes.put(route.name().toLowerCase(Locale.ROOT), route);
    }

    public void clear() {
        routes.clear();
    }
}
