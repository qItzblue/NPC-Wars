package com.npcwars.combat.brain;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.util.Vector;

/** Small aiming helpers shared by the throwing and shooting tactics. */
final class Aim {

    private Aim() {
    }

    /** @return the unit vector for a yaw and pitch in Minecraft's convention (pitch negative = up) */
    static Vector direction(double yawDegrees, double pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double horizontal = Math.cos(pitch);
        return new Vector(-Math.sin(yaw) * horizontal, -Math.sin(pitch), Math.cos(yaw) * horizontal);
    }

    /** A random error of up to {@code degrees} either way, so shots are not perfect. */
    static double error(double degrees) {
        return degrees <= 0 ? 0 : (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * degrees;
    }
}
