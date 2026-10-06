package com.npcwars.combat.brain;

import com.npcwars.combat.ItemRoles;
import com.npcwars.combat.Loadout;
import com.npcwars.config.Settings;

/** Moves a totem of undying into the off hand when health gets low (the game only lets a totem save you from a hand). */
final class TotemTactic implements Tactic {

    @Override
    public String name() {
        return "totem";
    }

    @Override
    public boolean enabled(Settings settings) {
        return settings.totems;
    }

    @Override
    public double want(CombatContext c) {
        if (!Loadout.has(c.loadout.totem) || c.loadout.totem == Hands.OFF_HAND || !c.brain.ready(name(), c.now)) {
            return 0;
        }
        if (c.health() > 10.0 || ItemRoles.isTotem(c.body.getInventory().getItemInMainHand().getType())) {
            return 0;
        }
        return 120.0;
    }

    @Override
    public boolean start(CombatContext c) {
        c.brain.lowerGuard();
        Hands.swap(c.body, c.loadout.totem, Hands.OFF_HAND);
        c.brain.cooldown(name(), c.now, 60);
        c.brain.dirty();
        return true;
    }

    @Override
    public boolean tick(CombatContext c) {
        return false;
    }

    @Override
    public void end(CombatContext c) {
        // nothing to release
    }
}
