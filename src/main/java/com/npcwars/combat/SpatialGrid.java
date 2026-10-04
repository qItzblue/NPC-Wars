package com.npcwars.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Uniform 2D grid for "nearest thing to this point" queries. Rebuilding is O(n); a query only looks at the cells
 * around the point, spiralling outwards until nothing closer can exist, so finding the nearest enemy among hundreds of
 * combatants touches a handful of them instead of all.
 */
public final class SpatialGrid<T extends SpatialGrid.Point> {

    /** Anything that can be placed in the grid. */
    public interface Point {
        double x();

        double y();

        double z();
    }

    /** Mutable result holder for one query. */
    private static final class Best<T> {
        T value;
        double distSq = Double.MAX_VALUE;
    }

    private final double cellSize;
    private final Map<Long, List<T>> cells = new HashMap<>();
    private int size;
    private int minCx = Integer.MAX_VALUE;
    private int maxCx = Integer.MIN_VALUE;
    private int minCz = Integer.MAX_VALUE;
    private int maxCz = Integer.MIN_VALUE;

    public SpatialGrid(double cellSize) {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be positive");
        }
        this.cellSize = cellSize;
    }

    public int size() {
        return size;
    }

    /** Empties the grid but keeps the cell lists around to avoid reallocating them every refresh. */
    public void clear() {
        if (cells.size() > 2048) {
            cells.clear();
        } else {
            cells.values().forEach(List::clear);
        }
        size = 0;
        minCx = Integer.MAX_VALUE;
        maxCx = Integer.MIN_VALUE;
        minCz = Integer.MAX_VALUE;
        maxCz = Integer.MIN_VALUE;
    }

    public void add(T point) {
        int cx = cell(point.x());
        int cz = cell(point.z());
        cells.computeIfAbsent(key(cx, cz), k -> new ArrayList<>(4)).add(point);
        size++;
        minCx = Math.min(minCx, cx);
        maxCx = Math.max(maxCx, cx);
        minCz = Math.min(minCz, cz);
        maxCz = Math.max(maxCz, cz);
    }

    /**
     * @param maxRadius largest distance to consider, or {@code <= 0} for no limit
     * @param filter    candidates for which this returns {@code false} are ignored
     * @return the closest accepted point (3D distance), or {@code null}
     */
    public T nearest(double x, double y, double z, double maxRadius, Predicate<? super T> filter) {
        if (size == 0) {
            return null;
        }
        int cx = cell(x);
        int cz = cell(z);
        int lastRing = Math.max(Math.max(Math.abs(cx - minCx), Math.abs(cx - maxCx)),
                Math.max(Math.abs(cz - minCz), Math.abs(cz - maxCz)));
        if (maxRadius > 0) {
            lastRing = Math.min(lastRing, (int) Math.ceil(maxRadius / cellSize));
        }
        double maxSq = maxRadius > 0 ? maxRadius * maxRadius : Double.MAX_VALUE;

        Best<T> best = new Best<>();
        for (int ring = 0; ring <= lastRing; ring++) {
            // Anything in this ring or further out is at least (ring - 1) whole cells away horizontally.
            double unseenMin = (ring - 1) * cellSize;
            if (best.value != null && ring >= 1 && best.distSq <= unseenMin * unseenMin) {
                break;
            }
            if (ring == 0) {
                scan(cx, cz, x, y, z, maxSq, filter, best);
                continue;
            }
            for (int dx = -ring; dx <= ring; dx++) {
                scan(cx + dx, cz - ring, x, y, z, maxSq, filter, best);
                scan(cx + dx, cz + ring, x, y, z, maxSq, filter, best);
            }
            for (int dz = -ring + 1; dz <= ring - 1; dz++) {
                scan(cx - ring, cz + dz, x, y, z, maxSq, filter, best);
                scan(cx + ring, cz + dz, x, y, z, maxSq, filter, best);
            }
        }
        return best.value;
    }

    private void scan(int cx, int cz, double x, double y, double z, double maxSq,
                      Predicate<? super T> filter, Best<T> best) {
        List<T> list = cells.get(key(cx, cz));
        if (list == null) {
            return;
        }
        for (T candidate : list) {
            double dx = candidate.x() - x;
            double dy = candidate.y() - y;
            double dz = candidate.z() - z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < best.distSq && distSq <= maxSq && filter.test(candidate)) {
                best.distSq = distSq;
                best.value = candidate;
            }
        }
    }

    private int cell(double coordinate) {
        return (int) Math.floor(coordinate / cellSize);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }
}
