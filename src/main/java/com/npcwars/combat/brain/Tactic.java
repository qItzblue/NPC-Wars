package com.npcwars.combat.brain;

import com.npcwars.config.Settings;

/**
 * One thing a fighting NPC can do with its items (launch itself with a wind charge, eat, throw a pearl...). The brain
 * asks every tactic how much it wants to run, starts the best one and ticks it until it is done. While a tactic runs it
 * owns the NPC's movement; the plain melee behaviour pauses.
 */
interface Tactic {

    String name();

    boolean enabled(Settings settings);

    /** @return {@code 0} for "not now", otherwise how badly this should run (the highest score starts) */
    double want(CombatContext c);

    /** @return {@code false} if it could not start (nothing was used up) */
    boolean start(CombatContext c);

    /** @return {@code true} while the tactic is still running */
    boolean tick(CombatContext c);

    /** Cleans up when the tactic finished or was interrupted: release items, hand the NPC back to melee. */
    void end(CombatContext c);
}
