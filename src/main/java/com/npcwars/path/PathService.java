package com.npcwars.path;

import com.npcwars.config.Settings;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Runs A* searches on the main thread under a strict per-tick time budget. NPCs file a {@link Request} and poll it on
 * later ticks, so a crowd that suddenly needs paths is spread over several ticks instead of stalling one.
 */
public final class PathService {

    /** A queued or finished search. Poll {@link #isDone()} and read {@link #path()}. */
    public static final class Request {
        private final World world;
        private final int sx;
        private final int sy;
        private final int sz;
        private final int gx;
        private final int gy;
        private final int gz;
        private final double goalRadius;
        private boolean done;
        private boolean cancelled;
        private Path path;

        private Request(World world, Location from, Location to, double goalRadius) {
            this.world = world;
            this.sx = from.getBlockX();
            this.sy = from.getBlockY();
            this.sz = from.getBlockZ();
            this.gx = to.getBlockX();
            this.gy = to.getBlockY();
            this.gz = to.getBlockZ();
            this.goalRadius = goalRadius;
        }

        public boolean isDone() {
            return done;
        }

        /** @return the planned path, or {@code null} if the search failed or has not finished */
        public Path path() {
            return path;
        }

        /** Marks the request as no longer wanted; it is skipped if it has not run yet. */
        public void cancel() {
            cancelled = true;
        }

        public int goalX() {
            return gx;
        }

        public int goalZ() {
            return gz;
        }
    }

    private final Settings settings;
    private final PathFinder finder = new PathFinder();
    private final Map<UUID, BukkitTerrain> terrains = new HashMap<>();
    private final ArrayDeque<Request> queue = new ArrayDeque<>();
    private long lastCacheClear;

    public PathService(Settings settings) {
        this.settings = settings;
    }

    /** @return the (cached) terrain view of a world */
    public Terrain terrain(World world) {
        return terrains.computeIfAbsent(world.getUID(), id -> new BukkitTerrain(world));
    }

    /** Queues a search from one location to another. */
    public Request request(Location from, Location to, double goalRadius) {
        Request request = new Request(from.getWorld(), from, to, goalRadius);
        queue.add(request);
        return request;
    }

    public int queued() {
        return queue.size();
    }

    /** Called once per server tick: expires terrain caches and serves queued searches within the time budget. */
    public void tick(long tick) {
        if (tick - lastCacheClear >= settings.pathCacheTtlTicks) {
            lastCacheClear = tick;
            terrains.entrySet().removeIf(entry -> Bukkit.getWorld(entry.getKey()) == null);
            terrains.values().forEach(BukkitTerrain::clearCache);
        }
        if (queue.isEmpty()) {
            return;
        }
        long started = System.nanoTime();
        PathFinder.Options options = new PathFinder.Options(settings.pathMaxNodes, settings.pathMaxRange,
                settings.pathMaxDrop, settings.pathHeuristicWeight, 0.0);
        while (!queue.isEmpty()) {
            Request request = queue.poll();
            if (request.cancelled) {
                continue;
            }
            PathFinder.Options perRequest = new PathFinder.Options(options.maxNodes(), options.maxRange(),
                    options.maxDrop(), options.heuristicWeight(), request.goalRadius);
            PathFinder.Result result = finder.find(terrain(request.world), request.sx, request.sy, request.sz,
                    request.gx, request.gy, request.gz, perRequest);
            request.path = result.status() == PathFinder.Status.FAILED ? null : result.path();
            request.done = true;
            if (System.nanoTime() - started >= settings.pathBudgetNanos) {
                break;
            }
        }
    }

    /**
     * Drops cached terrain and finishes every queued request as "no path", so NPCs waiting on a search fall back to
     * direct walking instead of waiting for a result that will never come.
     */
    public void shutdown() {
        for (Request request : queue) {
            request.done = true;
            request.path = null;
        }
        queue.clear();
        terrains.clear();
    }
}
