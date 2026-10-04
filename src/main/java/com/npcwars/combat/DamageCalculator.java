package com.npcwars.combat;

import java.util.List;

/**
 * Minecraft's attribute arithmetic and the small bits of combat math that do not need the server, kept pure so they
 * can be unit-tested.
 */
public final class DamageCalculator {

    /** How an attribute modifier combines with the value (mirrors Bukkit's {@code AttributeModifier.Operation}). */
    public enum Operation { ADD_NUMBER, ADD_SCALAR, MULTIPLY_SCALAR_1 }

    public record Modifier(Operation operation, double amount) {
    }

    /** Attack damage of an empty hand. */
    public static final double BASE_ATTACK_DAMAGE = 1.0;
    /** Attack speed (swings per second) of an empty hand. */
    public static final double BASE_ATTACK_SPEED = 4.0;
    private static final double CRITICAL_MULTIPLIER = 1.5;

    private DamageCalculator() {
    }

    /**
     * Applies modifiers the way the game does: all additions first, then the summed "add scalar" fraction of that
     * value, then each "multiply" in turn.
     */
    public static double resolve(double base, List<Modifier> modifiers) {
        double value = base;
        for (Modifier modifier : modifiers) {
            if (modifier.operation() == Operation.ADD_NUMBER) {
                value += modifier.amount();
            }
        }
        double result = value;
        for (Modifier modifier : modifiers) {
            if (modifier.operation() == Operation.ADD_SCALAR) {
                result += value * modifier.amount();
            }
        }
        for (Modifier modifier : modifiers) {
            if (modifier.operation() == Operation.MULTIPLY_SCALAR_1) {
                result *= 1.0 + modifier.amount();
            }
        }
        return result;
    }

    /** Ticks between full-strength swings for a given attack speed, never below {@code minTicks}. */
    public static int cooldownTicks(double attackSpeed, int minTicks) {
        if (attackSpeed <= 0.0) {
            return 20;
        }
        return Math.max(minTicks, (int) Math.ceil(20.0 / attackSpeed));
    }

    /** Extra damage from the Sharpness enchantment (0 for level 0). */
    public static double sharpnessBonus(int level) {
        return level <= 0 ? 0.0 : 0.5 * level + 0.5;
    }

    /** Damage after a critical hit. */
    public static double critical(double damage) {
        return damage * CRITICAL_MULTIPLIER;
    }
}
