package com.npcwars.combat.brain;

import com.npcwars.combat.Ballistics;
import com.npcwars.combat.Loadout;
import com.npcwars.config.Settings;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.entity.EnderPearl;
import org.bukkit.util.Vector;

/**
 * Ender pearls: to jump across a gap to a distant enemy, or to get away when nearly dead. An NPC body is not
 * teleported by the game when a pearl lands, so {@code CombatItemListener} does it (and applies the pearl damage).
 */
final class PearlTactic implements Tactic {

    private static final double SPEED = 1.5;

    private long startedAt;
    private Location destination;

    @Override
    public String name() {
        return "pearl";
    }

    @Override
    public boolean enabled(Settings settings) {
        return settings.usePearls;
    }

    @Override
    public double want(CombatContext c) {
        if (!Loadout.has(c.loadout.pearl) || !c.brain.ready(name(), c.now) || c.npc.controller().isAirborne()) {
            return 0;
        }
        var random = ThreadLocalRandom.current();
        if (c.reach > 18.0 && c.sees && random.nextDouble() < 0.5) {
            destination = c.target.getLocation();
            return 55.0;
        }
        if (c.health() <= 6.0 && c.reach < 7.0 && random.nextDouble() < 0.3) {
            Vector away = c.body.getLocation().toVector().subtract(c.target.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 1.0E-6) {
                Location spot = c.body.getLocation().add(away.normalize().multiply(16.0));
                spot.setY(c.body.getWorld().getHighestBlockYAt(spot) + 1.0);
                destination = spot;
                return 80.0;
            }
        }
        return 0;
    }

    @Override
    public boolean start(CombatContext c) {
        if (!Loadout.has(c.loadout.pearl) || destination == null) {
            return false;
        }
        c.brain.lowerGuard();
        Hands.select(c.body, c.loadout.pearl);
        c.npc.controller().stop();
        c.npc.controller().setAutoFace(false);
        startedAt = c.now;
        return true;
    }

    @Override
    public boolean tick(CombatContext c) {
        Location eye = c.body.getEyeLocation();
        Ballistics.Aim aim = Ballistics.solve(destination.getX() - eye.getX(), destination.getY() - eye.getY(),
                destination.getZ() - eye.getZ(), SPEED, Ballistics.THROWN_GRAVITY, Ballistics.DRAG);
        c.npc.controller().setLook((float) aim.yaw(), (float) aim.pitch());
        if (c.now - startedAt < 3) {
            return true;
        }
        int slot = Loadout.scan(c.body.getInventory().getContents()).pearl;
        if (!Loadout.has(slot)) {
            return false;
        }
        Vector direction = Aim.direction(aim.yaw(), aim.pitch()).multiply(SPEED);
        c.body.launchProjectile(EnderPearl.class, direction);
        c.brain.trace("throws an ender pearl");
        Hands.consumeOne(c.body, slot);
        c.brain.cooldown(name(), c.now, 160);
        return false;
    }

    @Override
    public void end(CombatContext c) {
        c.npc.controller().setAutoFace(true);
        c.brain.dirty();
    }
}
