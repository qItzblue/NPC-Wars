package com.npcwars.combat.brain;

import com.npcwars.combat.Loadout;
import com.npcwars.config.Settings;
import com.npcwars.npc.control.NpcController;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.WindCharge;
import org.bukkit.util.Vector;

/**
 * The wind charge slam: throw a wind charge at its own feet to be launched into the air, steer over the enemy, switch
 * to the mace at the top and smash down. The smash damage grows with the height fallen (see
 * {@code DamageCalculator.maceBonus}); after a smash the NPC takes no fall damage.
 *
 * <p>An NPC body gets no knockback from an explosion by itself (the game sends that to the client), so the launch comes
 * from the plugin's wind charge handling in {@code CombatItemListener}.
 */
final class WindMaceTactic implements Tactic {

    private enum Phase { PREP, RISE, FALL }

    private static final long COOLDOWN_TICKS = 160;
    private static final double AIR_CONTROL = 0.045;
    private static final double MAX_AIR_SPEED = 0.45;
    /** Upward speed of a normal wind charge jump (rises about 6 blocks). */
    private static final double MIN_LAUNCH = 1.0;

    private Phase phase = Phase.PREP;
    private long phaseStart;
    private boolean maceSelected;
    private boolean boosted;

    @Override
    public String name() {
        return "windmace";
    }

    @Override
    public boolean enabled(Settings settings) {
        return settings.maceSlam;
    }

    @Override
    public double want(CombatContext c) {
        if (!Loadout.has(c.loadout.mace) || !Loadout.has(c.loadout.windCharge) || !c.brain.ready(name(), c.now)) {
            return 0;
        }
        NpcController controller = c.npc.controller();
        if (controller.isAirborne() || !c.sees || c.health() < 6.0) {
            return 0;
        }
        if (c.horizontal < 3.0 || c.horizontal > 12.0 || Math.abs(c.target.getY() - c.body.getY()) > 5.0) {
            return 0;
        }
        double score = 70.0 + (c.brain.targetStunned(c.target, c.now) ? 25.0 : 0.0);
        return ThreadLocalRandom.current().nextDouble() < 0.7 ? score : 0.0;
    }

    @Override
    public boolean start(CombatContext c) {
        Hands.select(c.body, c.loadout.windCharge);
        c.npc.controller().stop();
        c.npc.controller().setAutoFace(false);
        phase = Phase.PREP;
        phaseStart = c.now;
        maceSelected = false;
        boosted = false;
        return true;
    }

    @Override
    public boolean tick(CombatContext c) {
        long t = c.now - phaseStart;
        NpcController controller = c.npc.controller();
        switch (phase) {
            case PREP -> {
                float yaw = (float) Math.toDegrees(Math.atan2(-(c.target.getX() - c.body.getX()), c.target.getZ() - c.body.getZ()));
                controller.setLook(yaw, 85f);
                if (t >= 3) {
                    int slot = findWindCharge(c);
                    if (slot == Loadout.NONE) {
                        return false;
                    }
                    c.body.launchProjectile(WindCharge.class, new Vector(0, -1, 0));
                    c.brain.trace("throws a wind charge at its feet");
                    Hands.consumeOne(c.body, slot);
                    controller.cushionLanding(120); // a wind burst launch spares the landing, as in the game
                    phase = Phase.RISE;
                    phaseStart = c.now;
                    boosted = false;
                }
                return true;
            }
            case RISE -> {
                if (t == 3 && !boosted) {
                    // The game's own explosion knockback launches the body, but how hard depends on where the charge
                    // landed. If it was a weak push, top it up to a normal wind charge jump (about 6 blocks).
                    Vector v = c.body.getVelocity();
                    boosted = true;
                    if (v.getY() < MIN_LAUNCH) {
                        c.body.setVelocity(new Vector(v.getX(), MIN_LAUNCH, v.getZ()));
                        c.brain.trace("weak launch, topped up");
                    }
                }
                steer(c);
                if (c.body.getVelocity().getY() <= 0.05 && t >= 3 || t > 40) {
                    c.brain.trace(String.format(java.util.Locale.ROOT, "reached the top at y=%.1f, switching to the mace", c.body.getY()));
                    selectMace(c);
                    phase = Phase.FALL;
                    phaseStart = c.now;
                }
                return true;
            }
            default -> {
                steer(c);
                if (!maceSelected) {
                    selectMace(c);
                }
                double fall = controller.fallDistance();
                if (fall >= 1.5 && c.reach <= c.settings.attackReach + 0.5) {
                    c.plugin.attacks().strike(c.npc, c.target);
                    return false;
                }
                return controller.isAirborne() && t <= 80;
            }
        }
    }

    private static int findWindCharge(CombatContext c) {
        return Loadout.scan(c.body.getInventory().getContents()).windCharge;
    }

    private void selectMace(CombatContext c) {
        int slot = Loadout.scan(c.body.getInventory().getContents()).mace;
        if (Loadout.has(slot)) {
            Hands.select(c.body, slot);
        }
        maceSelected = true;
    }

    /** Air control: a little acceleration towards the enemy each tick, like holding a movement key in the air. */
    private void steer(CombatContext c) {
        double dx = c.target.getX() - c.body.getX();
        double dz = c.target.getZ() - c.body.getZ();
        double length = Math.hypot(dx, dz);
        c.npc.controller().face(c.target.getLocation().add(0, c.target.getHeight() * 0.6, 0));
        if (length < 0.5) {
            return;
        }
        Vector v = c.body.getVelocity();
        double nx = v.getX() + dx / length * AIR_CONTROL;
        double nz = v.getZ() + dz / length * AIR_CONTROL;
        double speed = Math.hypot(nx, nz);
        if (speed > MAX_AIR_SPEED) {
            nx = nx / speed * MAX_AIR_SPEED;
            nz = nz / speed * MAX_AIR_SPEED;
        }
        c.body.setVelocity(new Vector(nx, v.getY(), nz));
    }

    @Override
    public void end(CombatContext c) {
        c.npc.controller().setAutoFace(true);
        c.brain.cooldown(name(), c.now, COOLDOWN_TICKS);
        c.brain.dirty();
    }
}
