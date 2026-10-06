package com.npcwars.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.npcwars.life.LifePlanner.Activity;
import com.npcwars.life.LifePlanner.Weights;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LifePlannerTest {

    private final Random random = new Random(11);

    @Test
    void choicesFollowTheWeights() {
        Weights weights = new Weights(60, 30, 0, 0, 0, 10);
        Map<Activity, Integer> counts = new EnumMap<>(Activity.class);
        for (int i = 0; i < 30_000; i++) {
            counts.merge(LifePlanner.choose(random, weights), 1, Integer::sum);
        }
        assertEquals(null, counts.get(Activity.JUMP));
        assertEquals(null, counts.get(Activity.SNEAK));
        assertEquals(null, counts.get(Activity.LOOK_AROUND));
        assertEquals(0.6, counts.get(Activity.WANDER) / 30_000.0, 0.02);
        assertEquals(0.3, counts.get(Activity.IDLE) / 30_000.0, 0.02);
        assertEquals(0.1, counts.get(Activity.SWING) / 30_000.0, 0.02);
    }

    @Test
    void allZeroWeightsJustStand() {
        for (int i = 0; i < 20; i++) {
            assertEquals(Activity.IDLE, LifePlanner.choose(random, new Weights(0, 0, 0, 0, 0, 0)));
        }
        assertEquals(Activity.IDLE, LifePlanner.choose(random, new Weights(-5, 0, 0, 0, 0, 0)));
    }

    @Test
    void everyActivityCanBeChosen() {
        Weights weights = new Weights(1, 1, 1, 1, 1, 1);
        Map<Activity, Integer> counts = new EnumMap<>(Activity.class);
        for (int i = 0; i < 6_000; i++) {
            counts.merge(LifePlanner.choose(random, weights), 1, Integer::sum);
        }
        assertEquals(Activity.values().length, counts.size());
    }

    @Test
    void delaysStayInsideTheBounds() {
        for (int i = 0; i < 1000; i++) {
            int ticks = LifePlanner.ticksBetween(random, 60, 200);
            assertTrue(ticks >= 60 && ticks <= 200);
        }
        assertEquals(40, LifePlanner.ticksBetween(random, 40, 40));
        assertTrue(LifePlanner.ticksBetween(random, 200, 60) >= 60);
    }

    @Test
    void wanderPointsLieInTheRing() {
        for (int i = 0; i < 2000; i++) {
            double[] offset = LifePlanner.ringOffset(random, 3, 10);
            double distance = Math.hypot(offset[0], offset[1]);
            assertTrue(distance >= 3 - 1e-9 && distance <= 10 + 1e-9, "distance " + distance);
        }
    }
}
