package com.npcwars.life;

import java.util.random.RandomGenerator;

/** The dice behind NPC life: what to do next, for how long, and where to wander. Pure so it can be tested. */
public final class LifePlanner {

    public enum Activity { WANDER, IDLE, LOOK_AROUND, JUMP, SNEAK, SWING }

    /** Relative likelihoods (any non-negative numbers; all zero means "just stand"). */
    public record Weights(int wander, int idle, int lookAround, int jump, int sneak, int swing) {
        public Weights {
            wander = Math.max(0, wander);
            idle = Math.max(0, idle);
            lookAround = Math.max(0, lookAround);
            jump = Math.max(0, jump);
            sneak = Math.max(0, sneak);
            swing = Math.max(0, swing);
        }

        int total() {
            return wander + idle + lookAround + jump + sneak + swing;
        }
    }

    private LifePlanner() {
    }

    public static Activity choose(RandomGenerator random, Weights weights) {
        int total = weights.total();
        if (total <= 0) {
            return Activity.IDLE;
        }
        int roll = random.nextInt(total);
        if ((roll -= weights.wander()) < 0) {
            return Activity.WANDER;
        }
        if ((roll -= weights.idle()) < 0) {
            return Activity.IDLE;
        }
        if ((roll -= weights.lookAround()) < 0) {
            return Activity.LOOK_AROUND;
        }
        if ((roll -= weights.jump()) < 0) {
            return Activity.JUMP;
        }
        if ((roll -= weights.sneak()) < 0) {
            return Activity.SNEAK;
        }
        return Activity.SWING;
    }

    /** @return a random number of ticks between the two bounds (inclusive) */
    public static int ticksBetween(RandomGenerator random, int minTicks, int maxTicks) {
        int low = Math.max(1, Math.min(minTicks, maxTicks));
        int high = Math.max(low, Math.max(minTicks, maxTicks));
        return low + random.nextInt(high - low + 1);
    }

    /**
     * @return {@code {dx, dz}}: a point uniformly spread over the ring between the two distances around the origin
     */
    public static double[] ringOffset(RandomGenerator random, double minDistance, double maxDistance) {
        double min = Math.max(0.0, Math.min(minDistance, maxDistance));
        double max = Math.max(min, maxDistance);
        double distance = Math.sqrt(min * min + random.nextDouble() * (max * max - min * min));
        double angle = random.nextDouble() * Math.PI * 2.0;
        return new double[] {Math.cos(angle) * distance, Math.sin(angle) * distance};
    }
}
