package com.npcwars.stick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AreaTest {

    @Test
    void cornersInAnyOrderGiveTheSameBox() {
        Area a = Area.of(10, 70, 5, 2, 64, 9);
        assertEquals(new Area(2, 64, 5, 10, 70, 9), a);
        assertEquals(9, a.sizeX());
        assertEquals(7, a.sizeY());
        assertEquals(5, a.sizeZ());
        assertEquals(9L * 7 * 5, a.volume());
    }

    @Test
    void containsIncludesTheEdgesAndHeadroom() {
        Area a = Area.of(0, 64, 0, 4, 64, 4);
        assertTrue(a.contains(0.0, 64.0, 0.0));
        assertTrue(a.contains(4.99, 64.0, 4.99));
        assertFalse(a.contains(5.0, 64.0, 2.0));
        assertFalse(a.contains(-0.01, 64.0, 2.0));
        assertTrue(a.contains(2.0, 65.5, 2.0), "a player standing on the top block is inside");
        assertFalse(a.contains(2.0, 66.5, 2.0));
        assertFalse(a.contains(2.0, 63.9, 2.0));
    }

    @Test
    void gridSpotsStayInsideAndRespectTheSpacing() {
        Area a = Area.of(0, 64, 0, 9, 64, 9);
        List<double[]> spots = a.gridSpots(3, 1000);
        assertEquals(16, spots.size(), "4 x 4 spots in a 10 x 10 area at spacing 3");
        for (double[] spot : spots) {
            assertTrue(a.contains(spot[0], 64, spot[1]), spot[0] + "," + spot[1]);
        }
        double first = spots.get(0)[0];
        double second = spots.get(4)[0];
        assertEquals(3.0, second - first, 1e-9);
    }

    @Test
    void gridSpotsAreCappedAndAlwaysHaveAtLeastOne() {
        assertEquals(5, Area.of(0, 0, 0, 40, 0, 40).gridSpots(1, 5).size());
        List<double[]> single = Area.of(7, 0, 7, 7, 0, 7).gridSpots(3, 10);
        assertEquals(1, single.size());
        assertEquals(7.5, single.get(0)[0], 1e-9);
        assertEquals(7.5, single.get(0)[1], 1e-9);
    }

    @Test
    void modesAndBehaviorsCycleAndParse() {
        assertEquals(StickMode.FILL_AREA, StickMode.COPY_PLAYERS.next());
        assertEquals(StickMode.SINGLE, StickMode.FILL_AREA.next());
        assertEquals(StickMode.COPY_PLAYERS, StickMode.SINGLE.next());
        assertEquals(StickBehavior.WALK_FORWARD, StickBehavior.STAND.toggled());
        assertEquals(StickBehavior.STAND, StickBehavior.WALK_FORWARD.toggled());
        assertEquals(StickMode.FILL_AREA, StickMode.parse("Fill", StickMode.SINGLE));
        assertEquals(StickMode.SINGLE, StickMode.parse("nonsense", StickMode.SINGLE));
        assertEquals(StickBehavior.WALK_FORWARD, StickBehavior.parse("walk", StickBehavior.STAND));
    }
}
