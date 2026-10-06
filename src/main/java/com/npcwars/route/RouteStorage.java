package com.npcwars.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/** Reads and writes the {@code routes} section of data.yml. Waypoints are stored as {@code "x,y,z"} strings. */
public final class RouteStorage {

    private RouteStorage() {
    }

    public static void save(RouteManager manager, ConfigurationSection root) {
        root.set("routes", null);
        ConfigurationSection routes = root.createSection("routes");
        for (Route route : manager.all()) {
            ConfigurationSection section = routes.createSection(route.name());
            section.set("world", route.worldName());
            List<String> points = new ArrayList<>();
            for (Route.Point point : route.points()) {
                points.add(String.format(Locale.ROOT, "%.2f,%.2f,%.2f", point.x(), point.y(), point.z()));
            }
            section.set("points", points);
        }
    }

    public static void load(RouteManager manager, ConfigurationSection root, Logger log) {
        manager.clear();
        ConfigurationSection routes = root.getConfigurationSection("routes");
        if (routes == null) {
            return;
        }
        for (String name : routes.getKeys(false)) {
            ConfigurationSection section = routes.getConfigurationSection(name);
            String world = section == null ? null : section.getString("world");
            if (!RouteManager.validName(name) || world == null) {
                log.warning("Ignoring route '" + name + "' in data.yml (bad name or no world)");
                continue;
            }
            Route route = new Route(name, world);
            for (String raw : section.getStringList("points")) {
                Route.Point point = parsePoint(raw);
                if (point == null) {
                    log.warning("Route " + name + ": ignoring bad waypoint '" + raw + "'");
                } else {
                    route.add(point);
                }
            }
            manager.restore(route);
        }
    }

    static Route.Point parsePoint(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
                return new Route.Point(x, y, z);
            }
        } catch (NumberFormatException ex) {
            // falls through
        }
        return null;
    }
}
