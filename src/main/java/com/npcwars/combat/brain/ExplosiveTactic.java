package com.npcwars.combat.brain;

import com.npcwars.combat.Loadout;
import com.npcwars.config.Settings;
import com.npcwars.npc.control.NpcController;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.util.Vector;

/**
 * Explosives: light TNT at the enemy's feet, or drop an end crystal there and pop it. The NPC runs away from the blast
 * while it burns. The explosion hurts entities as usual, but only breaks blocks if {@code combat.explosions-break-blocks}
 * is on (see {@code CombatItemListener}).
 */
final class ExplosiveTactic implements Tactic {

    private enum Kind { TNT, CRYSTAL }

    private Kind kind;
    private long startedAt;
    private EnderCrystal crystal;

    @Override
    public String name() {
        return "explosive";
    }

    @Override
    public boolean enabled(Settings settings) {
        return settings.tnt || settings.endCrystals;
    }

    @Override
    public double want(CombatContext c) {
        kind = null;
        if (!c.brain.ready(name(), c.now) || !c.sees || c.health() < 12.0 || ThreadLocalRandom.current().nextDouble() >= 0.35) {
            return 0;
        }
        if (c.settings.tnt && Loadout.has(c.loadout.tnt) && c.reach >= 3.5 && c.reach <= 9.0) {
            kind = Kind.TNT;
            return 40.0;
        }
        if (c.settings.endCrystals && Loadout.has(c.loadout.crystal) && c.reach >= 5.0 && c.reach <= 10.0) {
            kind = Kind.CRYSTAL;
            return 42.0;
        }
        return 0;
    }

    @Override
    public boolean start(CombatContext c) {
        if (kind == null) {
            return false;
        }
        int slot = kind == Kind.TNT ? c.loadout.tnt : c.loadout.crystal;
        if (!Loadout.has(slot)) {
            return false;
        }
        c.brain.lowerGuard();
        Hands.select(c.body, slot);
        c.npc.controller().stop();
        c.npc.controller().setAutoFace(false);
        startedAt = c.now;
        crystal = null;
        return true;
    }

    @Override
    public boolean tick(CombatContext c) {
        long t = c.now - startedAt;
        NpcController controller = c.npc.controller();
        if (t < 3) {
            controller.face(c.target.getLocation());
            return true;
        }
        if (t == 3) {
            Loadout now = Loadout.scan(c.body.getInventory().getContents());
            Location at = c.target.getLocation();
            c.body.swingMainHand();
            c.brain.trace("lights " + kind.name().toLowerCase(java.util.Locale.ROOT));
            if (kind == Kind.TNT && Loadout.has(now.tnt)) {
                TNTPrimed tnt = at.getWorld().spawn(at, TNTPrimed.class, entity -> {
                    entity.setFuseTicks(30);
                    entity.setSource(c.body);
                });
                c.plugin.explosives().add(tnt.getUniqueId());
                Hands.consumeOne(c.body, now.tnt);
            } else if (kind == Kind.CRYSTAL && Loadout.has(now.crystal)) {
                crystal = at.getWorld().spawn(at, EnderCrystal.class, entity -> entity.setShowingBottom(false));
                Hands.consumeOne(c.body, now.crystal);
            } else {
                return false;
            }
        }
        // Run away from the blast: the crystal goes off a few ticks after it appears, the TNT after its fuse.
        Vector away = c.body.getLocation().toVector().subtract(c.target.getLocation().toVector()).setY(0);
        if (away.lengthSquared() > 1.0E-6) {
            controller.walkDirection(away, NpcController.Gait.SPRINT);
        }
        if (crystal != null && t == 8 && crystal.isValid()) {
            // The crystal pops with the strength of the real thing (power 6); blocks only break if the config allows it.
            Location at = crystal.getLocation();
            crystal.remove();
            at.getWorld().createExplosion(at, 6.0f, false, c.settings.explosionsBreakBlocks, c.body);
        }
        return t < 40;
    }

    @Override
    public void end(CombatContext c) {
        c.npc.controller().setAutoFace(true);
        c.npc.controller().stop();
        c.brain.cooldown(name(), c.now, 300 + ThreadLocalRandom.current().nextInt(200));
        c.brain.dirty();
    }
}
