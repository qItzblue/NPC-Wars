package com.npcwars.action.builtin;

import com.npcwars.action.ActionContext;
import com.npcwars.action.ActionException;
import com.npcwars.action.NpcAction;
import com.npcwars.action.PreparedAction;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.npc.control.NpcController;
import com.npcwars.util.Completions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * {@code walk}, {@code run} and {@code swim}: keep walking in a direction for a number of blocks or a duration.
 * All three share the parsing and differ only in gait and stance.
 */
public final class DirectionalMoveAction implements NpcAction {

    /** The three flavours of directional movement. */
    public enum Mode { WALK, RUN, SWIM }

    private final String name;
    private final List<String> aliases;
    private final Mode mode;
    private final String description;

    public DirectionalMoveAction(String name, List<String> aliases, Mode mode, String description) {
        this.name = name;
        this.aliases = aliases;
        this.mode = mode;
        this.description = description;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<String> aliases() {
        return aliases;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String usage() {
        return "[direction] [blocks]";
    }

    @Override
    public PreparedAction prepare(ActionContext context, List<String> args) throws ActionException {
        Direction direction = Direction.FORWARD;
        double blocks = 0;
        for (String arg : args) {
            Direction parsed = Direction.parse(arg);
            if (parsed != null) {
                direction = parsed;
                continue;
            }
            try {
                blocks = Double.parseDouble(arg);
            } catch (NumberFormatException ex) {
                throw new ActionException("action.bad-argument", Messages.var("argument", arg), Messages.var("action", name));
            }
            if (blocks <= 0 || blocks > 10_000) {
                throw new ActionException("action.bad-argument", Messages.var("argument", arg), Messages.var("action", name));
            }
        }
        long fallback = context.plugin().settings().defaultActionSeconds * 20L;
        return new Prepared(direction, blocks, context.origin(), fallback);
    }

    @Override
    public List<String> complete(ActionContext context, List<String> args) {
        String last = args.isEmpty() ? "" : args.get(args.size() - 1);
        List<String> options = new ArrayList<>(Direction.NAMES);
        options.add("5");
        options.add("10");
        options.add("20");
        return Completions.filter(options, last);
    }

    private final class Prepared implements PreparedAction {
        private final Direction direction;
        private final double blocks;
        private final Location origin;
        private final long fallbackTicks;
        private final Map<Integer, double[]> startPositions = new HashMap<>();

        Prepared(Direction direction, double blocks, Location origin, long fallbackTicks) {
            this.direction = direction;
            this.blocks = blocks;
            this.origin = origin;
            this.fallbackTicks = fallbackTicks;
        }

        @Override
        public void start(Npc npc) {
            NpcController controller = npc.controller();
            Vector vector;
            try {
                vector = direction.resolve(npc, origin);
            } catch (ActionException ex) {
                return;
            }
            Location here = npc.currentLocation();
            if (here != null) {
                startPositions.put(npc.id(), new double[] {here.getX(), here.getZ()});
            }
            controller.setSwimming(mode == Mode.SWIM);
            controller.walkDirection(vector, mode == Mode.RUN ? NpcController.Gait.SPRINT : NpcController.Gait.WALK);
        }

        @Override
        public boolean tick(Npc npc, long elapsedTicks) {
            if (blocks <= 0) {
                return true;
            }
            double[] begin = startPositions.get(npc.id());
            Location here = npc.currentLocation();
            if (begin == null || here == null) {
                return false;
            }
            return Math.hypot(here.getX() - begin[0], here.getZ() - begin[1]) < blocks;
        }

        @Override
        public void stop(Npc npc) {
            startPositions.remove(npc.id());
            npc.controller().stop();
            if (mode == Mode.SWIM) {
                npc.controller().setSwimming(false);
            }
        }

        @Override
        public long defaultDurationTicks() {
            // With a block count the duration is only a safety net against an NPC that is stuck behind a wall.
            return blocks > 0 ? Math.max(fallbackTicks, (long) (blocks * 12)) : fallbackTicks;
        }
    }
}
