package com.npcwars.action.builtin;

import com.npcwars.action.ActionException;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/** Walking directions understood by the movement actions. */
enum Direction {
    NORTH, SOUTH, EAST, WEST, FORWARD, BACK, LEFT, RIGHT, TOWARD, AWAY;

    static final List<String> NAMES = Arrays.stream(values()).map(d -> d.name().toLowerCase(Locale.ROOT)).toList();

    /** @return the direction named by the text, or {@code null} if it is not a direction */
    static Direction parse(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "n" -> NORTH;
            case "s" -> SOUTH;
            case "e" -> EAST;
            case "w" -> WEST;
            case "forwards", "fwd" -> FORWARD;
            case "backward", "backwards" -> BACK;
            case "me", "towards", "tome" -> TOWARD;
            default -> {
                for (Direction direction : values()) {
                    if (direction.name().equalsIgnoreCase(lower)) {
                        yield direction;
                    }
                }
                yield null;
            }
        };
    }

    /**
     * Resolves the horizontal unit vector for one NPC.
     *
     * @param origin where the sender stood when the command ran (needed for TOWARD/AWAY), may be {@code null}
     */
    Vector resolve(Npc npc, Location origin) throws ActionException {
        Location here = npc.currentLocation();
        double yaw = Math.toRadians(here == null ? npc.yaw() : here.getYaw());
        Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        switch (this) {
            case NORTH:
                return new Vector(0, 0, -1);
            case SOUTH:
                return new Vector(0, 0, 1);
            case EAST:
                return new Vector(1, 0, 0);
            case WEST:
                return new Vector(-1, 0, 0);
            case FORWARD:
                return forward;
            case BACK:
                return forward.multiply(-1);
            case LEFT:
                return new Vector(forward.getZ(), 0, -forward.getX());
            case RIGHT:
                return new Vector(-forward.getZ(), 0, forward.getX());
            default:
                break;
        }
        if (origin == null || here == null || origin.getWorld() != here.getWorld()) {
            throw new ActionException("action.need-player-in-world", Messages.var("direction", name().toLowerCase(Locale.ROOT)));
        }
        Vector toward = new Vector(origin.getX() - here.getX(), 0, origin.getZ() - here.getZ());
        if (toward.lengthSquared() < 1.0E-6) {
            return forward;
        }
        toward.normalize();
        return this == TOWARD ? toward : toward.multiply(-1);
    }
}
