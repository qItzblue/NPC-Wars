package com.npcwars.combat;

import java.util.random.RandomGenerator;

/**
 * Makes NPC fighting less robotic: most swings connect but some miss, and now and then an NPC pauses before the next
 * swing instead of attacking the instant its weapon is ready. Pure maths so it can be tested without a server.
 */
public final class AttackPacing {

    /**
     * @param hitChance        probability that a swing connects (the others are swings through the air)
     * @param hesitateChance   probability of an extra pause after a swing
     * @param hesitateMinTicks shortest extra pause
     * @param hesitateMaxTicks longest extra pause
     * @param jitterTicks      random 0..n ticks added to every cooldown
     */
    public record Params(double hitChance, double hesitateChance, int hesitateMinTicks, int hesitateMaxTicks, int jitterTicks) {
        public Params {
            hitChance = Math.max(0.0, Math.min(1.0, hitChance));
            hesitateChance = Math.max(0.0, Math.min(1.0, hesitateChance));
            hesitateMinTicks = Math.max(0, hesitateMinTicks);
            hesitateMaxTicks = Math.max(hesitateMinTicks, hesitateMaxTicks);
            jitterTicks = Math.max(0, jitterTicks);
        }
    }

    private AttackPacing() {
    }

    /** @return {@code true} if this swing should land */
    public static boolean connects(RandomGenerator random, Params params) {
        return random.nextDouble() < params.hitChance();
    }

    /**
     * @param cooldownTicks the weapon's own cooldown, which is never shortened
     * @return ticks to wait before the next swing: the cooldown plus jitter plus an occasional hesitation
     */
    public static int nextDelay(RandomGenerator random, int cooldownTicks, Params params) {
        int delay = Math.max(1, cooldownTicks);
        if (params.jitterTicks() > 0) {
            delay += random.nextInt(params.jitterTicks() + 1);
        }
        if (random.nextDouble() < params.hesitateChance()) {
            delay += params.hesitateMinTicks() + random.nextInt(params.hesitateMaxTicks() - params.hesitateMinTicks() + 1);
        }
        return delay;
    }
}
