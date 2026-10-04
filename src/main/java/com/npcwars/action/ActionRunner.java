package com.npcwars.action;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.npc.Npc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Ticks the {@link PreparedAction}s that are currently running. An NPC runs at most one action at a time; starting a
 * new one stops the old one first.
 */
public final class ActionRunner {

    private static final class Active {
        final PreparedAction action;
        final long startedTick;
        final long limitTicks;

        Active(PreparedAction action, long startedTick, long limitTicks) {
            this.action = action;
            this.startedTick = startedTick;
            this.limitTicks = limitTicks;
        }
    }

    private final NpcWarsPlugin plugin;
    private final Map<Integer, Active> active = new HashMap<>();

    public ActionRunner(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts an action on an NPC.
     *
     * @param explicitTicks the sender's {@code for=} duration, or {@code 0} to use the action's own default
     */
    public void start(Npc npc, PreparedAction action, long explicitTicks) {
        stop(npc);
        long limit = explicitTicks > 0 ? explicitTicks : action.defaultDurationTicks();
        active.put(npc.id(), new Active(action, plugin.currentTick(), limit));
        action.start(npc);
    }

    /** Stops whatever the NPC is doing; the movement controller is left as the action left it. */
    public void stop(Npc npc) {
        Active running = active.remove(npc.id());
        if (running != null) {
            running.action.stop(npc);
        }
    }

    public void stopAll() {
        for (Npc npc : new ArrayList<>(plugin.npcs().all())) {
            stop(npc);
        }
        active.clear();
    }

    public boolean isRunning(Npc npc) {
        return active.containsKey(npc.id());
    }

    public int runningCount() {
        return active.size();
    }

    public void tick(long tick) {
        if (active.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, Active>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Active> entry = iterator.next();
            Npc npc = plugin.npcs().get(entry.getKey());
            Active running = entry.getValue();
            if (npc == null) {
                iterator.remove();
                continue;
            }
            if (!npc.isLive()) {
                // Dead or unloaded: drop the action instead of freezing it until the body comes back.
                iterator.remove();
                running.action.stop(npc);
                continue;
            }
            long elapsed = tick - running.startedTick;
            boolean keepGoing = running.action.tick(npc, elapsed);
            if (!keepGoing || (running.limitTicks > 0 && elapsed >= running.limitTicks)) {
                iterator.remove();
                running.action.stop(npc);
            }
        }
    }
}
