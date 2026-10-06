package com.npcwars.action.builtin;

import com.npcwars.action.ActionContext;
import com.npcwars.action.ActionException;
import com.npcwars.action.NpcAction;
import com.npcwars.action.PreparedAction;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.npc.control.NpcController;
import com.npcwars.util.Completions;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * {@code move} sends every NPC to a place and finishes when it arrives. The destination is the sender
 * ({@code me}), coordinates, each NPC's home ({@code home}) or a random spot around it ({@code random [radius]}).
 * Pathfinding is used when the straight way is blocked. Add {@code run} or {@code sneak} to change the gait.
 */
public final class MoveAction implements NpcAction {

    private static final long TIMEOUT_TICKS = 90 * 20L;

    private enum Target { FIXED, HOME, RANDOM }

    @Override
    public String name() {
        return "move";
    }

    @Override
    public List<String> aliases() {
        return List.of("goto", "go");
    }

    @Override
    public String description() {
        return "Walk to a place: me, x y z, home or random [radius]";
    }

    @Override
    public String usage() {
        return "<me|x y z|home|random [radius]> [run|march|sneak]";
    }

    @Override
    public PreparedAction prepare(ActionContext context, List<String> args) throws ActionException {
        if (args.isEmpty()) {
            throw new ActionException("action.missing-argument", Messages.var("action", name()), Messages.var("usage", usage()));
        }
        NpcController.Gait gait = NpcController.Gait.WALK;
        for (String arg : args) {
            switch (arg.toLowerCase(Locale.ROOT)) {
                case "run", "sprint" -> gait = NpcController.Gait.SPRINT;
                case "sneak" -> gait = NpcController.Gait.SNEAK;
                case "march" -> gait = NpcController.Gait.MARCH;
                default -> { }
            }
        }

        String first = args.get(0).toLowerCase(Locale.ROOT);
        switch (first) {
            case "me", "here" -> {
                Location origin = context.origin();
                if (origin == null) {
                    throw new ActionException("action.need-player-in-world", Messages.var("direction", first));
                }
                return new Prepared(Target.FIXED, origin, 0, gait);
            }
            case "home", "spawn" -> {
                return new Prepared(Target.HOME, null, 0, gait);
            }
            case "random", "wander" -> {
                int radius = 10;
                if (args.size() > 1 && !args.get(1).isEmpty() && args.get(1).length() <= 6
                        && args.get(1).chars().allMatch(Character::isDigit)) {
                    radius = Math.max(2, Math.min(200, Integer.parseInt(args.get(1))));
                }
                return new Prepared(Target.RANDOM, null, radius, gait);
            }
            default -> {
                if (args.size() < 3) {
                    throw new ActionException("action.missing-argument", Messages.var("action", name()), Messages.var("usage", usage()));
                }
                try {
                    double x = Double.parseDouble(args.get(0));
                    double y = Double.parseDouble(args.get(1));
                    double z = Double.parseDouble(args.get(2));
                    World world = context.origin() == null ? null : context.origin().getWorld();
                    return new Prepared(Target.FIXED, new Location(world, x, y, z), 0, gait);
                } catch (NumberFormatException ex) {
                    throw new ActionException("action.bad-argument", Messages.var("argument", args.get(0)), Messages.var("action", name()));
                }
            }
        }
    }

    @Override
    public List<String> complete(ActionContext context, List<String> args) {
        String last = args.isEmpty() ? "" : args.get(args.size() - 1);
        if (args.size() <= 1) {
            return Completions.filter(List.of("me", "home", "random"), last);
        }
        if (args.get(0).equalsIgnoreCase("random") && args.size() == 2) {
            return Completions.filter(List.of("10", "25", "50", "run"), last);
        }
        return Completions.filter(List.of("run", "sneak"), last);
    }

    private static final class Prepared implements PreparedAction {
        private final Target target;
        private final Location fixed;
        private final int radius;
        private final NpcController.Gait gait;

        Prepared(Target target, Location fixed, int radius, NpcController.Gait gait) {
            this.target = target;
            this.fixed = fixed;
            this.radius = radius;
            this.gait = gait;
        }

        @Override
        public void start(Npc npc) {
            Location here = npc.currentLocation();
            if (here == null) {
                return;
            }
            Location destination = switch (target) {
                case HOME -> npc.home();
                case RANDOM -> randomAround(here);
                case FIXED -> fixed.getWorld() == null ? new Location(here.getWorld(), fixed.getX(), fixed.getY(), fixed.getZ()) : fixed;
            };
            if (destination != null) {
                npc.controller().moveTo(destination, gait, 1.2);
            }
        }

        @Override
        public boolean tick(Npc npc, long elapsedTicks) {
            return npc.controller().isMoving();
        }

        @Override
        public void stop(Npc npc) {
            npc.controller().stop();
        }

        @Override
        public long defaultDurationTicks() {
            return TIMEOUT_TICKS;
        }

        private Location randomAround(Location here) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double angle = random.nextDouble(Math.PI * 2);
            double distance = 3 + random.nextDouble(Math.max(1, radius - 3));
            double x = here.getX() + Math.cos(angle) * distance;
            double z = here.getZ() + Math.sin(angle) * distance;
            World world = here.getWorld();
            double y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1.0;
            if (Math.abs(y - here.getY()) > 8) {
                y = here.getY();
            }
            return new Location(world, x, y, z);
        }
    }
}
