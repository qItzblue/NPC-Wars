package com.npcwars.action;

import com.npcwars.npc.Npc;

/** An action with its arguments already parsed, ready to run on any number of NPCs. */
public interface PreparedAction {

    /** Called once when the action begins on an NPC. */
    default void start(Npc npc) {
    }

    /**
     * Called every server tick while the action runs on a live NPC.
     *
     * @param elapsedTicks ticks since {@link #start} for this NPC
     * @return {@code false} when the action is finished
     */
    boolean tick(Npc npc, long elapsedTicks);

    /** Called once when the action ends (finished, replaced, timed out or cancelled). Release any state here. */
    default void stop(Npc npc) {
    }

    /**
     * @return how many ticks the action may run when the sender gave no {@code for=} duration, or {@code 0} to run
     *         until {@link #tick} returns {@code false}
     */
    default long defaultDurationTicks() {
        return 0L;
    }
}
