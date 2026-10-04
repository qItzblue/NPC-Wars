package com.npcwars.path;

import java.util.List;

/** An ordered list of waypoints (standing cells) from the cell after the start to the goal or the closest reachable cell. */
public final class Path {

    /** A standing cell: the block the feet occupy. */
    public record Node(int x, int y, int z) {
        public double centerX() {
            return x + 0.5;
        }

        public double centerZ() {
            return z + 0.5;
        }
    }

    private final List<Node> nodes;
    private final boolean reachesGoal;

    public Path(List<Node> nodes, boolean reachesGoal) {
        this.nodes = List.copyOf(nodes);
        this.reachesGoal = reachesGoal;
    }

    public int size() {
        return nodes.size();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public Node get(int index) {
        return nodes.get(index);
    }

    public Node last() {
        return nodes.get(nodes.size() - 1);
    }

    public List<Node> nodes() {
        return nodes;
    }

    /** @return {@code false} for a partial path that only gets close to the goal */
    public boolean reachesGoal() {
        return reachesGoal;
    }
}
