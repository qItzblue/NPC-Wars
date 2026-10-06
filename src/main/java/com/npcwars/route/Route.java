package com.npcwars.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A named sequence of waypoints in one world. */
public final class Route {

    /** One waypoint. */
    public record Point(double x, double y, double z) {
    }

    private final String name;
    private final String worldName;
    private final List<Point> points = new ArrayList<>();

    public Route(String name, String worldName) {
        this.name = name;
        this.worldName = worldName;
    }

    public String name() {
        return name;
    }

    public String worldName() {
        return worldName;
    }

    public List<Point> points() {
        return Collections.unmodifiableList(points);
    }

    public int size() {
        return points.size();
    }

    public void add(Point point) {
        points.add(point);
    }

    /** @param index 1-based, as shown to users */
    public boolean remove(int index) {
        if (index < 1 || index > points.size()) {
            return false;
        }
        points.remove(index - 1);
        return true;
    }

    public void clear() {
        points.clear();
    }

    /** @return the walking length of the whole route in blocks (straight lines between waypoints) */
    public double length() {
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            Point a = points.get(i - 1);
            Point b = points.get(i);
            total += Math.sqrt((b.x() - a.x()) * (b.x() - a.x()) + (b.y() - a.y()) * (b.y() - a.y())
                    + (b.z() - a.z()) * (b.z() - a.z()));
        }
        return total;
    }
}
