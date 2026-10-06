package com.npcwars.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Grid A* for walking NPCs. A cell is a block position the feet occupy. Supported moves: walking (8 directions, no
 * corner cutting), one-block step ups (jumps), drops of up to {@link Options#maxDrop()} blocks and swimming.
 * Pure Java: all world access goes through {@link Terrain}.
 */
public final class PathFinder {

    /**
     * @param maxNodes        hard cap on expanded cells (bounds the cost of one search)
     * @param maxRange        search box half-width around the start, in blocks
     * @param maxDrop         largest fall the planner will accept
     * @param heuristicWeight 1.0 gives optimal paths; larger values trade optimality for fewer expansions
     * @param goalRadius      horizontal distance from the goal cell that counts as arrived
     */
    public record Options(int maxNodes, int maxRange, int maxDrop, double heuristicWeight, double goalRadius) {
    }

    public enum Status { FOUND, PARTIAL, FAILED }

    public record Result(Status status, Path path, int expanded) {
    }

    private static final double SQRT2 = Math.sqrt(2.0);
    private static final double JUMP_PENALTY = 0.6;
    private static final double WATER_FACTOR = 1.6;
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private static final class Node {
        final int x;
        final int y;
        final int z;
        double g;
        double f;
        Node parent;
        boolean closed;

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * Finds a path between two standing cells. The start and goal heights are adjusted to the nearest valid standing
     * cell (so a target that is mid-jump resolves to the ground below it).
     */
    public Result find(Terrain terrain, int sx, int sy, int sz, int gx, int gy, int gz, Options options) {
        int startY = resolveStand(terrain, sx, sy, sz, 1, 1);
        if (startY == Integer.MIN_VALUE) {
            return new Result(Status.FAILED, new Path(List.of(), false), 0);
        }
        int goalY = resolveStand(terrain, gx, gy, gz, 1, 8);
        if (goalY == Integer.MIN_VALUE) {
            return new Result(Status.FAILED, new Path(List.of(), false), 0);
        }

        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Map<Long, Node> all = new HashMap<>();
        Node start = new Node(sx, startY, sz);
        start.f = options.heuristicWeight() * heuristic(start.x, start.y, start.z, gx, goalY, gz);
        open.add(start);
        all.put(key(start.x, start.y, start.z), start);

        Node best = start;
        double bestH = heuristic(start.x, start.y, start.z, gx, goalY, gz);
        int expanded = 0;
        double radiusSq = options.goalRadius() * options.goalRadius();

        while (!open.isEmpty() && expanded < options.maxNodes()) {
            Node current = open.poll();
            if (current.closed) {
                continue;
            }
            current.closed = true;
            expanded++;

            int dxGoal = current.x - gx;
            int dzGoal = current.z - gz;
            if (dxGoal * dxGoal + dzGoal * dzGoal <= radiusSq && Math.abs(current.y - goalY) <= 1) {
                return new Result(Status.FOUND, build(start, current, true), expanded);
            }
            double h = heuristic(current.x, current.y, current.z, gx, goalY, gz);
            if (h < bestH) {
                bestH = h;
                best = current;
            }

            for (int[] dir : DIRECTIONS) {
                int nx = current.x + dir[0];
                int nz = current.z + dir[1];
                if (Math.abs(nx - sx) > options.maxRange() || Math.abs(nz - sz) > options.maxRange()) {
                    continue;
                }
                int ny = nextY(terrain, current, dir[0], dir[1], options.maxDrop());
                if (ny == Integer.MIN_VALUE) {
                    continue;
                }
                double cost = moveCost(terrain, current, dir[0] != 0 && dir[1] != 0, nx, ny, nz);
                double tentative = current.g + cost;
                long k = key(nx, ny, nz);
                Node neighbour = all.get(k);
                if (neighbour == null) {
                    neighbour = new Node(nx, ny, nz);
                    neighbour.g = Double.MAX_VALUE;
                    all.put(k, neighbour);
                }
                if (neighbour.closed || tentative >= neighbour.g) {
                    continue;
                }
                neighbour.g = tentative;
                neighbour.parent = current;
                neighbour.f = tentative + options.heuristicWeight() * heuristic(nx, ny, nz, gx, goalY, gz);
                open.add(neighbour);
            }
        }

        if (best == start) {
            return new Result(Status.FAILED, new Path(List.of(), false), expanded);
        }
        return new Result(Status.PARTIAL, build(start, best, false), expanded);
    }

    /** @return {@code true} if a body fits at the cell and something supports it (ground or water) */
    public static boolean canStand(Terrain terrain, int x, int y, int z) {
        int body = terrain.type(x, y, z);
        int head = terrain.type(x, y + 1, z);
        if (!Terrain.isPassable(body) || !Terrain.isPassable(head)) {
            return false;
        }
        int below = terrain.type(x, y - 1, z);
        if (below == Terrain.SOLID) {
            return true;
        }
        return body == Terrain.WATER && below != Terrain.HAZARD && below != Terrain.UNLOADED;
    }

    /**
     * Cheap line-of-walk test used to decide whether an NPC can simply walk straight at its target. Samples the line
     * every half block and requires a standing cell within one step up or three blocks down at each sample.
     */
    public static boolean straightWalkable(Terrain terrain, double x1, double y1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        int steps = (int) Math.ceil(Math.hypot(dx, dz) / 0.5);
        int y = (int) Math.floor(y1 + 0.01);
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (int i = 1; i <= steps; i++) {
            double f = i / (double) steps;
            int cx = (int) Math.floor(x1 + dx * f);
            int cz = (int) Math.floor(z1 + dz * f);
            if (cx == lastX && cz == lastZ) {
                continue;
            }
            lastX = cx;
            lastZ = cz;
            if (canStand(terrain, cx, y, cz)) {
                continue;
            }
            if (canStand(terrain, cx, y + 1, cz) && Terrain.isPassable(terrain.type(cx, y + 2, cz))) {
                y++;
                continue;
            }
            int landed = Integer.MIN_VALUE;
            for (int drop = 1; drop <= 3; drop++) {
                if (canStand(terrain, cx, y - drop, cz)) {
                    landed = y - drop;
                    break;
                }
                if (!Terrain.isPassable(terrain.type(cx, y - drop, cz))) {
                    break;
                }
            }
            if (landed == Integer.MIN_VALUE) {
                return false;
            }
            y = landed;
        }
        return true;
    }

    /**
     * Height of the cell reached by moving one block in the given direction, or {@link Integer#MIN_VALUE} if the move
     * is not possible. Tries level, then step up, then drop.
     */
    private static int nextY(Terrain t, Node from, int dx, int dz, int maxDrop) {
        int x = from.x;
        int y = from.y;
        int z = from.z;
        int nx = x + dx;
        int nz = z + dz;
        boolean diagonal = dx != 0 && dz != 0;
        if (diagonal && !(cellClear(t, x + dx, y, z) && cellClear(t, x, y, z + dz))) {
            return Integer.MIN_VALUE;
        }
        if (canStand(t, nx, y, nz)) {
            return y;
        }
        // Step up one block: needs room above the current cell for the jump.
        if (Terrain.isPassable(t.type(x, y + 2, z))
                && (!diagonal || (cellClear(t, x + dx, y + 1, z) && cellClear(t, x, y + 1, z + dz)))
                && canStand(t, nx, y + 1, nz)) {
            return y + 1;
        }
        // Walk off an edge and fall.
        if (cellClear(t, nx, y, nz)) {
            for (int drop = 1; drop <= maxDrop; drop++) {
                int ty = y - drop;
                if (canStand(t, nx, ty, nz)) {
                    return ty;
                }
                if (!Terrain.isPassable(t.type(nx, ty, nz))) {
                    break;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private static double moveCost(Terrain t, Node from, boolean diagonal, int nx, int ny, int nz) {
        double cost = diagonal ? SQRT2 : 1.0;
        if (ny > from.y) {
            cost += JUMP_PENALTY;
        } else if (ny < from.y) {
            cost += 0.3 * (from.y - ny);
        }
        if (t.type(nx, ny, nz) == Terrain.WATER) {
            cost *= WATER_FACTOR;
        }
        return cost;
    }

    private static boolean cellClear(Terrain t, int x, int y, int z) {
        return Terrain.isPassable(t.type(x, y, z)) && Terrain.isPassable(t.type(x, y + 1, z));
    }

    /** Finds the closest valid standing height: the given one, then {@code up} above, then {@code down} below. */
    private static int resolveStand(Terrain t, int x, int y, int z, int up, int down) {
        if (canStand(t, x, y, z)) {
            return y;
        }
        for (int i = 1; i <= down; i++) {
            if (canStand(t, x, y - i, z)) {
                return y - i;
            }
        }
        for (int i = 1; i <= up; i++) {
            if (canStand(t, x, y + i, z)) {
                return y + i;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static double heuristic(int x, int y, int z, int gx, int gy, int gz) {
        double dx = Math.abs(x - gx);
        double dz = Math.abs(z - gz);
        double octile = (dx + dz) + (SQRT2 - 2.0) * Math.min(dx, dz);
        return octile + Math.abs(y - gy) * 0.5;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) (y + 2048) & 0xFFFL);
    }

    /** Walks the parent chain, then merges runs of flat, same-direction steps into a single waypoint. */
    private static Path build(Node start, Node end, boolean complete) {
        List<Node> chain = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) {
            chain.add(n);
        }
        Collections.reverse(chain);
        List<Path.Node> out = new ArrayList<>();
        for (int i = 1; i < chain.size(); i++) {
            Node cur = chain.get(i);
            if (i < chain.size() - 1) {
                Node prev = chain.get(i - 1);
                Node next = chain.get(i + 1);
                boolean sameDirection = cur.x - prev.x == next.x - cur.x
                        && cur.z - prev.z == next.z - cur.z
                        && cur.y == prev.y && next.y == cur.y;
                if (sameDirection) {
                    continue;
                }
            }
            out.add(new Path.Node(cur.x, cur.y, cur.z));
        }
        return new Path(out, complete);
    }
}
