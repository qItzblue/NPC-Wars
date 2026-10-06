package com.npcwars.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class AttackPacingTest {

    private final Random random = new Random(42);

    @Test
    void mostSwingsConnectButNotAll() {
        AttackPacing.Params params = new AttackPacing.Params(0.85, 0, 0, 0, 0);
        int hits = 0;
        for (int i = 0; i < 20_000; i++) {
            if (AttackPacing.connects(random, params)) {
                hits++;
            }
        }
        double rate = hits / 20_000.0;
        assertTrue(rate > 0.82 && rate < 0.88, "hit rate was " + rate);
    }

    @Test
    void extremesAreExact() {
        assertTrue(AttackPacing.connects(random, new AttackPacing.Params(1.0, 0, 0, 0, 0)));
        assertTrue(!AttackPacing.connects(random, new AttackPacing.Params(0.0, 0, 0, 0, 0)));
    }

    @Test
    void withoutJitterOrHesitationTheCooldownIsExact() {
        AttackPacing.Params params = new AttackPacing.Params(1, 0, 4, 14, 0);
        for (int i = 0; i < 100; i++) {
            assertEquals(12, AttackPacing.nextDelay(random, 12, params));
        }
    }

    @Test
    void delayNeverDropsBelowTheCooldownAndHesitationStaysInRange() {
        AttackPacing.Params params = new AttackPacing.Params(1, 1.0, 4, 14, 2);
        for (int i = 0; i < 1000; i++) {
            int delay = AttackPacing.nextDelay(random, 10, params);
            assertTrue(delay >= 10 + 4 && delay <= 10 + 2 + 14, "delay " + delay);
        }
    }

    @Test
    void someSwingsAreDelayedAtTheConfiguredRate() {
        AttackPacing.Params params = new AttackPacing.Params(1, 0.12, 4, 14, 0);
        int paused = 0;
        for (int i = 0; i < 20_000; i++) {
            if (AttackPacing.nextDelay(random, 10, params) > 10) {
                paused++;
            }
        }
        double rate = paused / 20_000.0;
        assertTrue(rate > 0.10 && rate < 0.14, "pause rate was " + rate);
    }

    @Test
    void parametersAreClamped() {
        AttackPacing.Params params = new AttackPacing.Params(5, -1, 10, 3, -4);
        assertEquals(1.0, params.hitChance());
        assertEquals(0.0, params.hesitateChance());
        assertEquals(10, params.hesitateMaxTicks());
        assertEquals(0, params.jitterTicks());
    }
}
