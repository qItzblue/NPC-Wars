package com.npcwars.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BallisticsTest {

    @Test
    void anArrowAimedAtADistantTargetLandsOnIt() {
        for (double distance : new double[] {8, 15, 25, 40}) {
            Ballistics.Aim aim = Ballistics.solve(distance, 0, 0, 3.0, Ballistics.ARROW_GRAVITY, Ballistics.DRAG);
            assertTrue(aim.reachable(), "distance " + distance + " error " + aim.error());
            assertTrue(aim.error() < 0.3, "error " + aim.error());
            assertTrue(aim.pitch() < 0, "must aim above the horizon to counter gravity, was " + aim.pitch());
        }
    }

    @Test
    void fartherTargetsNeedAHigherArc() {
        double near = Ballistics.solve(10, 0, 0, 3.0, Ballistics.ARROW_GRAVITY, Ballistics.DRAG).pitch();
        double far = Ballistics.solve(40, 0, 0, 3.0, Ballistics.ARROW_GRAVITY, Ballistics.DRAG).pitch();
        assertTrue(far < near, "far " + far + " near " + near);
    }

    @Test
    void higherTargetsNeedAHigherAimAndLowerOnesALowerAim() {
        double level = Ballistics.solve(20, 0, 0, 3.0, Ballistics.ARROW_GRAVITY, Ballistics.DRAG).pitch();
        double above = Ballistics.solve(20, 6, 0, 3.0, Ballistics.ARROW_GRAVITY, Ballistics.DRAG).pitch();
        double below = Ballistics.solve(20, -6, 0, 3.0, Ballistics.ARROW_GRAVITY, Ballistics.DRAG).pitch();
        assertTrue(above < level && level < below, above + " " + level + " " + below);
    }

    @Test
    void yawPointsAtTheTarget() {
        assertEquals(0.0, Ballistics.solve(0, 0, 10, 3, 0.05, 0.99).yaw(), 1e-6, "south");
        assertEquals(90.0, Ballistics.solve(-10, 0, 0, 3, 0.05, 0.99).yaw(), 1e-6, "west");
        assertEquals(-90.0, Ballistics.solve(10, 0, 0, 3, 0.05, 0.99).yaw(), 1e-6, "east");
        assertEquals(180.0, Math.abs(Ballistics.solve(0, 0, -10, 3, 0.05, 0.99).yaw()), 1e-6, "north");
    }

    @Test
    void aTargetBeyondRangeIsReportedUnreachable() {
        Ballistics.Aim aim = Ballistics.solve(500, 0, 0, 1.5, Ballistics.THROWN_GRAVITY, Ballistics.DRAG);
        assertFalse(aim.reachable());
    }

    @Test
    void withoutGravityTheAimIsAStraightLine() {
        Ballistics.Aim aim = Ballistics.solve(10, 10, 0, 1.0, 0.0, 1.0);
        assertEquals(-45.0, aim.pitch(), 0.5);
    }

    @Test
    void straightUnderOrOverTheShooterAimsVertically() {
        assertEquals(90.0, Ballistics.solve(0, -3, 0, 1, 0.03, 0.99).pitch(), 1e-9);
        assertEquals(-90.0, Ballistics.solve(0, 3, 0, 1, 0.03, 0.99).pitch(), 1e-9);
    }
}
