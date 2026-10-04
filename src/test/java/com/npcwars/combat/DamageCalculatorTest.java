package com.npcwars.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.npcwars.combat.DamageCalculator.Modifier;
import com.npcwars.combat.DamageCalculator.Operation;
import java.util.List;
import org.junit.jupiter.api.Test;

class DamageCalculatorTest {

    private static final double EPS = 1.0E-9;

    @Test
    void emptyHandDoesBaseDamage() {
        assertEquals(1.0, DamageCalculator.resolve(DamageCalculator.BASE_ATTACK_DAMAGE, List.of()), EPS);
    }

    @Test
    void diamondSwordDoesSevenDamageAtOnePointSixSwingsPerSecond() {
        double damage = DamageCalculator.resolve(1.0, List.of(new Modifier(Operation.ADD_NUMBER, 6.0)));
        double speed = DamageCalculator.resolve(4.0, List.of(new Modifier(Operation.ADD_NUMBER, -2.4)));
        assertEquals(7.0, damage, EPS);
        assertEquals(1.6, speed, EPS);
        assertEquals(13, DamageCalculator.cooldownTicks(speed, 4), "20 / 1.6 = 12.5 rounds up");
    }

    @Test
    void axesSwingSlowly() {
        double speed = DamageCalculator.resolve(4.0, List.of(new Modifier(Operation.ADD_NUMBER, -3.0)));
        assertEquals(20, DamageCalculator.cooldownTicks(speed, 4));
    }

    @Test
    void additionsComeBeforeScalarsAndMultipliers() {
        List<Modifier> mods = List.of(
                new Modifier(Operation.MULTIPLY_SCALAR_1, 1.0),
                new Modifier(Operation.ADD_SCALAR, 0.5),
                new Modifier(Operation.ADD_NUMBER, 5.0));
        // (10 + 5) = 15; + 15 * 0.5 = 22.5; * (1 + 1.0) = 45
        assertEquals(45.0, DamageCalculator.resolve(10.0, mods), EPS);
    }

    @Test
    void severalAddScalarsShareTheSameBase() {
        List<Modifier> mods = List.of(new Modifier(Operation.ADD_SCALAR, 0.5), new Modifier(Operation.ADD_SCALAR, 0.25));
        assertEquals(17.5, DamageCalculator.resolve(10.0, mods), EPS);
    }

    @Test
    void cooldownHonoursTheMinimumAndGuardsBadSpeeds() {
        assertEquals(5, DamageCalculator.cooldownTicks(4.0, 4));
        assertEquals(4, DamageCalculator.cooldownTicks(100.0, 4));
        assertEquals(20, DamageCalculator.cooldownTicks(0.0, 4));
        assertEquals(20, DamageCalculator.cooldownTicks(-2.0, 4));
    }

    @Test
    void sharpnessAddsHalfALevelPlusHalf() {
        assertEquals(0.0, DamageCalculator.sharpnessBonus(0), EPS);
        assertEquals(1.0, DamageCalculator.sharpnessBonus(1), EPS);
        assertEquals(1.5, DamageCalculator.sharpnessBonus(2), EPS);
        assertEquals(3.0, DamageCalculator.sharpnessBonus(5), EPS);
    }

    @Test
    void criticalHitsDealOneAndAHalfTimes() {
        assertEquals(15.0, DamageCalculator.critical(10.0), EPS);
    }
}
