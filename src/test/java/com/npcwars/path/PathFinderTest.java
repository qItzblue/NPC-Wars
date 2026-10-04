package com.npcwars.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PathFinderTest {

    /** Everything at or below {@code floor} is solid; everything above is air, unless overridden. */
    private static final class FakeTerrain implements Terrain {
        private record Pos(int x, int y, int z) {
        }

        private final int floor;
        private final Map<Pos, Integer> overrides = new HashMap<>();

        FakeTerrain(int floor) {
            this.floor = floor;
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, int type) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        overrides.put(new Pos(x, y, z), type);
                    }
                }
            }
        }

        @Override
        public int type(int x, int y, int z) {
            Integer override = overrides.get(new Pos(x, y, z));
            if (override != null) {
                return override;
            }
            return y <= floor ? SOLID : AIR;
        }
    }

    private PathFinder finder;
    private PathFinder.Options options;

    @BeforeEach
    void setUp() {
        finder = new PathFinder();
        options = new PathFinder.Options(5000, 64, 3, 1.0, 0.0);
    }

    private PathFinder.Result find(Terrain terrain, int sx, int sy, int sz, int gx, int gy, int gz) {
        return finder.find(terrain, sx, sy, sz, gx, gy, gz, options);
    }

    private static void assertAllStandable(Terrain terrain, Path path) {
        for (Path.Node node : path.nodes()) {
            assertTrue(PathFinder.canStand(terrain, node.x(), node.y(), node.z()),
                    "waypoint " + node + " is not a valid standing cell");
        }
    }

    @Test
    void walksStraightAcrossOpenGround() {
        FakeTerrain terrain = new FakeTerrain(0);
        PathFinder.Result result = find(terrain, 0, 1, 0, 10, 1, 0);
        assertEquals(PathFinder.Status.FOUND, result.status());
        assertTrue(result.path().reachesGoal());
        assertEquals(new Path.Node(10, 1, 0), result.path().last());
        assertEquals(1, result.path().size(), "a straight line collapses to a single waypoint");
        assertAllStandable(terrain, result.path());
    }

    @Test
    void goesDiagonallyInsteadOfZigZagging() {
        FakeTerrain terrain = new FakeTerrain(0);
        PathFinder.Result result = find(terrain, 0, 1, 0, 6, 1, 6);
        assertEquals(PathFinder.Status.FOUND, result.status());
        assertEquals(1, result.path().size());
    }

    @Test
    void alreadyAtTheGoalGivesAnEmptyFoundPath() {
        FakeTerrain terrain = new FakeTerrain(0);
        PathFinder.Result result = find(terrain, 3, 1, 3, 3, 1, 3);
        assertEquals(PathFinder.Status.FOUND, result.status());
        assertTrue(result.path().isEmpty());
    }

    @Test
    void goesAroundAWall() {
        FakeTerrain terrain = new FakeTerrain(0);
        terrain.fill(5, 1, -6, 5, 3, 6, Terrain.SOLID);
        PathFinder.Result result = find(terrain, 0, 1, 0, 10, 1, 0);
        assertEquals(PathFinder.Status.FOUND, result.status());
        assertAllStandable(terrain, result.path());
        for (Path.Node node : result.path().nodes()) {
            assertFalse(node.x() == 5 && node.z() >= -6 && node.z() <= 6, "path goes through the wall at " + node);
        }
        assertTrue(result.path().size() > 1, "the detour needs at least one turn");
    }

    @Test
    void doesNotCutCornersBetweenTwoDiagonalWalls() {
        FakeTerrain terrain = new FakeTerrain(0);
        terrain.fill(1, 1, 0, 1, 3, 0, Terrain.SOLID);
        terrain.fill(0, 1, 1, 0, 3, 1, Terrain.SOLID);
        PathFinder.Result result = find(terrain, 0, 1, 0, 1, 1, 1);
        assertEquals(PathFinder.Status.FOUND, result.status());
        assertNotEquals(new Path.Node(1, 1, 1), result.path().get(0), "the first step must not squeeze through the corner");
        assertTrue(result.path().size() > 1);
    }

    @Test
    void jumpsOntoAOneBlockStep() {
        FakeTerrain terrain = new FakeTerrain(0);
        terrain.fill(3, 1, -20, 20, 1, 20, Terrain.SOLID);
        PathFinder.Result result = find(terrain, 0, 1, 0, 6, 2, 0);
        assertEquals(PathFinder.Status.FOUND, result.status());
        assertEquals(2, result.path().last().y());
        assertAllStandable(terrain, result.path());
    }

    @Test
    void cannotClimbATwoBlockWall() {
        FakeTerrain terrain = new FakeTerrain(0);
        terrain.fill(3, 1, -20, 3, 2, 20, Terrain.SOLID);
        options = new PathFinder.Options(5000, 10, 3, 1.0, 0.0);
        PathFinder.Result result = find(terrain, 0, 1, 0, 6, 1, 0);
        assertNotEquals(PathFinder.Status.FOUND, result.status());
        if (result.status() == PathFinder.Status.PARTIAL) {
            assertFalse(result.path().reachesGoal());
            assertEquals(2, result.path().last().x(), "the partial path stops right in front of the wall");
        }
    }

    @Test
    void doesNotWalkOnToTheBlockBehindAGapOfLowHeadroom() {
        FakeTerrain terrain = new FakeTerrain(0);
        // A one-block step with a ceiling right above it: standing there would need 2 free blocks of headroom.
        terrain.fill(3, 1, -20, 20, 1, 20, Terrain.SOLID);
        terrain.fill(3, 3, -20, 20, 3, 20, Terrain.SOLID);
        options = new PathFinder.Options(5000, 10, 3, 1.0, 0.0);
        PathFinder.Result result = find(terrain, 0, 1, 0, 6, 2, 0);
        assertNotEquals(PathFinder.Status.FOUND, result.status());
    }

    @Test
    void dropsDownWithinTheConfiguredLimit() {
        FakeTerrain terrain = new FakeTerrain(-3);
        terrain.fill(-20, -2, -20, 2, 0, 20, Terrain.SOLID);
        PathFinder.Result ok = find(terrain, 0, 1, 0, 6, -2, 0);
        assertEquals(PathFinder.Status.FOUND, ok.status());
        assertEquals(-2, ok.path().last().y());

        options = new PathFinder.Options(5000, 64, 2, 1.0, 0.0);
        PathFinder.Result tooHigh = find(terrain, 0, 1, 0, 6, -2, 0);
        assertNotEquals(PathFinder.Status.FOUND, tooHigh.status(), "a 3-block drop is not allowed when maxDrop is 2");
    }

    @Test
    void swimsAcrossWater() {
        FakeTerrain terrain = new FakeTerrain(-1);
        // A pond across the whole width: surface cells are water at y=0 over a solid bottom at y=-2... depth of one.
        terrain.fill(3, 0, -30, 6, 0, 30, Terrain.WATER);
        terrain.fill(3, -1, -30, 6, -1, 30, Terrain.WATER);
        terrain.fill(3, -2, -30, 6, -2, 30, Terrain.SOLID);
        PathFinder.Result result = find(terrain, 0, 0, 0, 10, 0, 0);
        assertEquals(PathFinder.Status.FOUND, result.status());
        assertEquals(10, result.path().last().x());
    }

    @Test
    void neverEntersHazards() {
        FakeTerrain terrain = new FakeTerrain(0);
        // A lava-like strip longer than the search range, so there is no way around it.
        terrain.fill(5, 0, -30, 5, 0, 30, Terrain.HAZARD);
        options = new PathFinder.Options(5000, 8, 3, 1.0, 0.0);
        PathFinder.Result result = find(terrain, 0, 1, 0, 10, 1, 0);
        for (Path.Node node : result.path().nodes()) {
            assertNotEquals(5, node.x(), "path must not step on or over the hazard line");
        }
        assertNotEquals(PathFinder.Status.FOUND, result.status());
    }

    @Test
    void walksAroundAHazardWhenThereIsAWay() {
        FakeTerrain terrain = new FakeTerrain(0);
        terrain.fill(5, 0, -4, 5, 0, 4, Terrain.HAZARD);
        PathFinder.Result result = find(terrain, 0, 1, 0, 10, 1, 0);
        assertEquals(PathFinder.Status.FOUND, result.status());
        for (Path.Node node : result.path().nodes()) {
            assertFalse(node.x() == 5 && Math.abs(node.z()) <= 4, "path crosses the hazard at " + node);
        }
        assertAllStandable(terrain, result.path());
    }

    @Test
    void refusesUnloadedChunks() {
        Terrain terrain = (x, y, z) -> x > 4 ? Terrain.UNLOADED : (y <= 0 ? Terrain.SOLID : Terrain.AIR);
        PathFinder.Result result = find(terrain, 0, 1, 0, 8, 1, 0);
        assertNotEquals(PathFinder.Status.FOUND, result.status());
        assertTrue(result.path().nodes().stream().noneMatch(n -> n.x() > 4));
    }

    @Test
    void failsWhenStartHasNoGround() {
        Terrain terrain = (x, y, z) -> Terrain.AIR;
        assertEquals(PathFinder.Status.FAILED, find(terrain, 0, 50, 0, 5, 50, 0).status());
    }

    @Test
    void snapsAGoalThatIsMidAirDownToTheGround() {
        FakeTerrain terrain = new FakeTerrain(0);
        PathFinder.Result result = find(terrain, 0, 1, 0, 8, 5, 0);
        assertEquals(PathFinder.Status.FOUND, result.status());
        assertEquals(1, result.path().last().y());
    }

    @Test
    void respectsTheNodeBudget() {
        FakeTerrain terrain = new FakeTerrain(0);
        options = new PathFinder.Options(50, 64, 3, 1.0, 0.0);
        PathFinder.Result result = find(terrain, 0, 1, 0, 60, 1, 60);
        assertTrue(result.expanded() <= 50, "expanded " + result.expanded());
        assertNotEquals(PathFinder.Status.FOUND, result.status());
    }

    @Test
    void goalRadiusStopsEarly() {
        FakeTerrain terrain = new FakeTerrain(0);
        options = new PathFinder.Options(5000, 64, 3, 1.0, 3.0);
        PathFinder.Result result = find(terrain, 0, 1, 0, 10, 1, 0);
        assertEquals(PathFinder.Status.FOUND, result.status());
        int lastX = result.path().last().x();
        assertTrue(lastX >= 7 && lastX < 10, "stops within 3 blocks of the goal, was " + lastX);
    }

    @Test
    void heuristicWeightKeepsPathsValid() {
        FakeTerrain terrain = new FakeTerrain(0);
        terrain.fill(5, 1, -8, 5, 3, 8, Terrain.SOLID);
        options = new PathFinder.Options(5000, 64, 3, 2.5, 0.0);
        PathFinder.Result result = find(terrain, 0, 1, 0, 10, 1, 0);
        assertEquals(PathFinder.Status.FOUND, result.status());
        assertAllStandable(terrain, result.path());
    }

    @Test
    void straightWalkableDetectsClearLinesWallsAndPits() {
        FakeTerrain terrain = new FakeTerrain(0);
        assertTrue(PathFinder.straightWalkable(terrain, 0.5, 1, 0.5, 12.5, 0.5));

        terrain.fill(6, 1, -3, 6, 3, 3, Terrain.SOLID);
        assertFalse(PathFinder.straightWalkable(terrain, 0.5, 1, 0.5, 12.5, 0.5), "a wall blocks the line");

        FakeTerrain pit = new FakeTerrain(0);
        pit.fill(5, -10, -3, 7, 0, 3, Terrain.AIR);
        assertFalse(PathFinder.straightWalkable(pit, 0.5, 1, 0.5, 12.5, 0.5), "a deep pit blocks the line");
    }

    @Test
    void straightWalkableAllowsAOneBlockStep() {
        FakeTerrain terrain = new FakeTerrain(0);
        terrain.fill(6, 1, -3, 20, 1, 3, Terrain.SOLID);
        assertTrue(PathFinder.straightWalkable(terrain, 0.5, 1, 0.5, 12.5, 0.5));
    }
}
