package com.npcwars.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SpatialGridTest {

    private record P(int id, double x, double y, double z) implements SpatialGrid.Point {
    }

    private static P nearestBruteForce(List<P> points, double x, double y, double z, double radius,
                                       java.util.function.Predicate<P> filter) {
        P best = null;
        double bestSq = Double.MAX_VALUE;
        double maxSq = radius > 0 ? radius * radius : Double.MAX_VALUE;
        for (P p : points) {
            double d = (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y) + (p.z - z) * (p.z - z);
            if (d < bestSq && d <= maxSq && filter.test(p)) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }

    @Test
    void emptyGridFindsNothing() {
        SpatialGrid<P> grid = new SpatialGrid<>(16);
        assertNull(grid.nearest(0, 0, 0, 0, p -> true));
    }

    @Test
    void findsTheNearestPoint() {
        SpatialGrid<P> grid = new SpatialGrid<>(16);
        P near = new P(1, 3, 0, 4);
        grid.add(new P(2, 100, 0, 100));
        grid.add(near);
        grid.add(new P(3, -40, 0, 8));
        assertSame(near, grid.nearest(0, 0, 0, 0, p -> true));
    }

    @Test
    void respectsTheFilterAndTheRadius() {
        SpatialGrid<P> grid = new SpatialGrid<>(16);
        grid.add(new P(1, 2, 0, 0));
        grid.add(new P(2, 30, 0, 0));
        assertEquals(2, grid.nearest(0, 0, 0, 0, p -> p.id() == 2).id());
        assertNull(grid.nearest(0, 0, 0, 10, p -> p.id() == 2), "point 2 is outside the radius");
        assertNull(grid.nearest(0, 0, 0, 0, p -> false));
    }

    @Test
    void matchesBruteForceOnRandomData() {
        Random random = new Random(42);
        for (int round = 0; round < 20; round++) {
            SpatialGrid<P> grid = new SpatialGrid<>(16);
            List<P> points = new ArrayList<>();
            int count = 1 + random.nextInt(250);
            for (int i = 0; i < count; i++) {
                P p = new P(i, random.nextDouble() * 400 - 200, random.nextDouble() * 40, random.nextDouble() * 400 - 200);
                points.add(p);
                grid.add(p);
            }
            for (int q = 0; q < 100; q++) {
                double x = random.nextDouble() * 500 - 250;
                double y = random.nextDouble() * 40;
                double z = random.nextDouble() * 500 - 250;
                double radius = random.nextBoolean() ? 0 : 10 + random.nextDouble() * 150;
                int skip = random.nextInt(count);
                java.util.function.Predicate<P> filter = p -> p.id() % 7 != skip % 7;
                P expected = nearestBruteForce(points, x, y, z, radius, filter);
                P actual = grid.nearest(x, y, z, radius, filter);
                if (expected == null) {
                    assertNull(actual, "round " + round + " query " + q);
                } else {
                    assertEquals(expected.id(), actual == null ? -1 : actual.id(), "round " + round + " query " + q);
                }
            }
        }
    }

    @Test
    void clearEmptiesTheGridAndItCanBeRefilled() {
        SpatialGrid<P> grid = new SpatialGrid<>(8);
        grid.add(new P(1, 0, 0, 0));
        grid.add(new P(2, 50, 0, 50));
        assertEquals(2, grid.size());
        grid.clear();
        assertEquals(0, grid.size());
        assertNull(grid.nearest(0, 0, 0, 0, p -> true));
        P again = new P(3, 5, 0, 5);
        grid.add(again);
        assertSame(again, grid.nearest(0, 0, 0, 0, p -> true));
    }

    @Test
    void handlesNegativeCoordinatesAndCellBoundaries() {
        SpatialGrid<P> grid = new SpatialGrid<>(16);
        P a = new P(1, -0.01, 0, -0.01);
        P b = new P(2, 0.01, 0, 0.01);
        grid.add(a);
        grid.add(b);
        assertSame(a, grid.nearest(-1, 0, -1, 0, p -> true));
        assertSame(b, grid.nearest(1, 0, 1, 0, p -> true));
    }

    @Test
    void rejectsANonPositiveCellSize() {
        assertThrows(IllegalArgumentException.class, () -> new SpatialGrid<P>(0));
    }
}
