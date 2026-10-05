package com.npcwars.route;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.combat.FightManager;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.npc.control.NpcController;
import com.npcwars.util.TimeParser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Walks NPCs along a {@link Route}, waypoint by waypoint. When the last NPC of a run has reached the end (or died, or
 * got stuck for too long) the staff is told, and a fight can be started automatically.
 */
public final class RouteRunner {

    /** What happens when everyone has finished. */
    public enum Finish { NOTHING, FIGHT_NOW, FIGHT_AFTER }

    /**
     * @param spread       how far NPCs fan out around each waypoint so they do not stack on one spot
     * @param fightDelaySeconds used with {@link Finish#FIGHT_AFTER}
     */
    public record Options(NpcController.Gait gait, double spread, Finish finish, long fightDelaySeconds) {
    }

    private static final double ARRIVE_DISTANCE = 1.5;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private static final class Run {
        final Route route;
        final Options options;
        final Set<Integer> active = new HashSet<>();
        int total;

        Run(Route route, Options options) {
            this.route = route;
            this.options = options;
        }
    }

    private static final class Walker {
        final Run run;
        final double dx;
        final double dz;
        int index;
        boolean commanded;
        long lastProgress;

        Walker(Run run, double dx, double dz) {
            this.run = run;
            this.dx = dx;
            this.dz = dz;
        }
    }

    private final NpcWarsPlugin plugin;
    private final Map<Integer, Walker> walkers = new HashMap<>();

    public RouteRunner(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isOnRoute(Npc npc) {
        return walkers.containsKey(npc.id());
    }

    public int activeCount() {
        return walkers.size();
    }

    /** @return the route this NPC is walking, or {@code null} */
    public String routeOf(Npc npc) {
        Walker walker = walkers.get(npc.id());
        return walker == null ? null : walker.run.route.name();
    }

    /**
     * Starts the NPCs on the route. NPCs that are not alive or are in another world are skipped.
     *
     * @return how many NPCs started walking
     */
    public int start(Route route, Collection<Npc> npcs, Options options) {
        World world = org.bukkit.Bukkit.getWorld(route.worldName());
        if (world == null || route.size() == 0) {
            return 0;
        }
        Run run = new Run(route, options);
        java.util.List<Npc> chosen = new ArrayList<>();
        for (Npc npc : npcs) {
            if (npc.isLive() && npc.entity().getWorld() == world) {
                chosen.add(npc);
            }
        }
        int n = chosen.size();
        for (int i = 0; i < n; i++) {
            Npc npc = chosen.get(i);
            release(npc);
            plugin.runner().stop(npc);
            npc.controller().reset();
            double radius = n <= 1 ? 0.0 : options.spread() * Math.sqrt((i + 0.5) / n);
            Walker walker = new Walker(run, Math.cos(i * GOLDEN_ANGLE) * radius, Math.sin(i * GOLDEN_ANGLE) * radius);
            walker.lastProgress = plugin.currentTick();
            walkers.put(npc.id(), walker);
            run.active.add(npc.id());
        }
        run.total = n;
        return n;
    }

    /** Takes one NPC off its route (it stays where it is). */
    public void release(Npc npc) {
        Walker walker = walkers.remove(npc.id());
        if (walker != null) {
            walker.run.active.remove(npc.id());
            if (npc.isLive()) {
                npc.controller().stop();
            }
        }
    }

    /** Stops every route walker without finishing the runs (no fight is started). */
    public int cancelAll() {
        int count = walkers.size();
        for (Integer id : new ArrayList<>(walkers.keySet())) {
            Npc npc = plugin.npcs().get(id);
            if (npc != null) {
                release(npc);
            }
        }
        walkers.clear();
        return count;
    }

    /** Stops the walkers of one route. */
    public int cancel(Route route) {
        int count = 0;
        for (Map.Entry<Integer, Walker> entry : new ArrayList<>(walkers.entrySet())) {
            if (entry.getValue().run.route == route) {
                Npc npc = plugin.npcs().get(entry.getKey());
                if (npc != null) {
                    release(npc);
                } else {
                    walkers.remove(entry.getKey());
                }
                count++;
            }
        }
        return count;
    }

    /** Called once per server tick. */
    public void tick(long tick) {
        if (walkers.isEmpty()) {
            return;
        }
        long timeoutTicks = plugin.settings().routeWaypointTimeoutTicks;
        for (Map.Entry<Integer, Walker> entry : new ArrayList<>(walkers.entrySet())) {
            Walker walker = entry.getValue();
            if (walkers.get(entry.getKey()) != walker) {
                continue;
            }
            Npc npc = plugin.npcs().get(entry.getKey());
            if (npc == null || !npc.isLive()) {
                finish(entry.getKey(), walker);
                continue;
            }
            Route route = walker.run.route;
            World world = npc.entity().getWorld();
            NpcController controller = npc.controller();
            if (!walker.commanded) {
                Route.Point point = route.points().get(walker.index);
                controller.moveTo(new Location(world, point.x() + walker.dx, point.y(), point.z() + walker.dz),
                        walker.run.options.gait(), ARRIVE_DISTANCE);
                walker.commanded = true;
                walker.lastProgress = tick;
            }
            if (controller.hasArrived()) {
                advance(npc, walker);
            } else if (tick - walker.lastProgress > timeoutTicks) {
                advance(npc, walker); // stuck behind something: skip the waypoint so the sequence can still complete
            } else if (!controller.isMoving()) {
                walker.commanded = false; // something stopped it; send it again
            }
        }
    }

    private void advance(Npc npc, Walker walker) {
        walker.index++;
        walker.commanded = false;
        if (walker.index >= walker.run.route.size()) {
            npc.controller().stop();
            finish(npc.id(), walker);
        }
    }

    private void finish(int npcId, Walker walker) {
        walkers.remove(npcId);
        Run run = walker.run;
        run.active.remove(npcId);
        if (run.active.isEmpty()) {
            complete(run);
        }
    }

    private void complete(Run run) {
        Options options = run.options;
        plugin.messages().notifyStaff("route.finished", Messages.var("route", run.route.name()),
                Messages.var("count", run.total));
        switch (options.finish()) {
            case FIGHT_NOW -> report(plugin.fights().startNow(), 0);
            case FIGHT_AFTER -> report(plugin.fights().schedule(options.fightDelaySeconds()), options.fightDelaySeconds());
            default -> plugin.messages().notifyStaff("route.finished-hint");
        }
    }

    private void report(FightManager.StartResult result, long seconds) {
        switch (result) {
            case STARTED -> plugin.messages().notifyStaff("route.fight-started");
            case SCHEDULED -> plugin.messages().notifyStaff("route.fight-scheduled", Messages.var("time", TimeParser.format(seconds)));
            case ALREADY_RUNNING -> plugin.messages().notifyStaff("fight.already-running");
            case NO_NPCS -> plugin.messages().notifyStaff("fight.no-npcs");
            case NOT_ENOUGH_SIDES -> plugin.messages().notifyStaff("route.fight-not-possible");
        }
    }
}
