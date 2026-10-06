package com.npcwars.combat;

/**
 * Aiming for thrown and shot projectiles. Projectiles move like in the game: each tick the position advances by the
 * velocity, then the velocity is multiplied by the drag and gravity is subtracted. Pure maths, tested without a server.
 */
public final class Ballistics {

    /** Arrow and trident physics. */
    public static final double ARROW_GRAVITY = 0.05;
    /** Ender pearl, snowball, egg physics. */
    public static final double THROWN_GRAVITY = 0.03;
    /** Thrown potion physics. */
    public static final double POTION_GRAVITY = 0.05;
    public static final double DRAG = 0.99;

    /**
     * @param yaw       degrees, Minecraft convention (0 = south, 90 = west)
     * @param pitch     degrees, negative looks up
     * @param reachable whether the best arc passes within about a block of the target
     * @param error     how far (blocks) the best arc misses the target
     */
    public record Aim(double yaw, double pitch, boolean reachable, double error) {
    }

    private Ballistics() {
    }

    /**
     * Finds the flattest arc that hits a point.
     *
     * @param dx,dy,dz target minus shooter, in blocks
     * @param speed    initial speed in blocks per tick
     */
    public static Aim solve(double dx, double dy, double dz, double speed, double gravity, double drag) {
        double horizontal = Math.hypot(dx, dz);
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        if (horizontal < 1.0E-6) {
            return new Aim(yaw, dy >= 0 ? -90.0 : 90.0, true, 0.0);
        }
        double bestPitch = 0.0;
        double bestError = Double.MAX_VALUE;
        for (double pitchUp = -30.0; pitchUp <= 70.0; pitchUp += 0.25) {
            double error = missDistance(horizontal, dy, pitchUp, speed, gravity, drag);
            if (error < bestError - 1.0E-9) {
                bestError = error;
                bestPitch = pitchUp;
            }
        }
        // Minecraft's pitch is negative upwards.
        return new Aim(yaw, -bestPitch, bestError < 1.0, bestError);
    }

    /**
     * Flies a projectile launched {@code pitchUp} degrees above the horizon and measures how far it passes from the
     * target at the moment it reaches the target's horizontal distance (or, if it never gets there, how far it falls short).
     */
    static double missDistance(double horizontal, double dy, double pitchUp, double speed, double gravity, double drag) {
        double vx = speed * Math.cos(Math.toRadians(pitchUp));
        double vy = speed * Math.sin(Math.toRadians(pitchUp));
        double x = 0;
        double y = 0;
        for (int tick = 0; tick < 400; tick++) {
            double nx = x + vx;
            double ny = y + vy;
            if (nx >= horizontal) {
                double t = vx <= 0 ? 0 : (horizontal - x) / vx;
                double yAt = y + vy * t;
                return Math.abs(yAt - dy);
            }
            x = nx;
            y = ny;
            vx *= drag;
            vy = vy * drag - gravity;
            if (y < dy - 80) {
                break;
            }
        }
        return Math.hypot(horizontal - x, dy - y) + 50;
    }
}
