package com.npcwars.combat.brain;

import com.npcwars.combat.Ballistics;
import com.npcwars.combat.ItemRoles;
import com.npcwars.combat.Loadout;
import com.npcwars.config.Settings;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Things to throw: harming and poison splash potions at the enemy, a healing splash potion at its own feet when hurt,
 * fire charges, snowballs and eggs. The NPC holds the item up for a couple of ticks first.
 */
final class ThrowTactic implements Tactic {

    private enum Kind { HARM_POTION, SELF_HEAL, FIRE_CHARGE, SNOWBALL }

    private Kind kind;
    private int slot = Loadout.NONE;
    private long startedAt;

    @Override
    public String name() {
        return "throw";
    }

    @Override
    public boolean enabled(Settings settings) {
        return settings.throwables || settings.potions;
    }

    @Override
    public double want(CombatContext c) {
        kind = null;
        slot = Loadout.NONE;
        if (!c.brain.ready(name(), c.now) || !c.sees) {
            return 0;
        }
        Settings s = c.settings;
        var random = ThreadLocalRandom.current();
        double best = 0;
        if (s.potions) {
            if (c.health() <= s.eatBelowHealth) {
                int potion = findThrowable(c, PotionKinds.Kind.HEAL);
                if (Loadout.has(potion)) {
                    kind = Kind.SELF_HEAL;
                    slot = potion;
                    best = 85.0;
                }
            }
            if (best == 0 && c.reach >= 4.0 && c.reach <= 16.0 && random.nextDouble() < 0.5) {
                int potion = findThrowable(c, PotionKinds.Kind.HARM);
                if (Loadout.has(potion)) {
                    kind = Kind.HARM_POTION;
                    slot = potion;
                    best = 50.0;
                }
            }
        }
        if (best == 0 && s.throwables && c.reach >= 5.0 && c.reach <= 20.0 && random.nextDouble() < 0.4) {
            if (Loadout.has(c.loadout.fireCharge)) {
                kind = Kind.FIRE_CHARGE;
                slot = c.loadout.fireCharge;
                best = 35.0;
            } else if (Loadout.has(c.loadout.snowball) && !Loadout.has(c.loadout.bow) && !Loadout.has(c.loadout.crossbow)) {
                kind = Kind.SNOWBALL;
                slot = c.loadout.snowball;
                best = 15.0;
            }
        }
        return best;
    }

    private int findThrowable(CombatContext c, PotionKinds.Kind wanted) {
        for (int potionSlot : c.loadout.potions) {
            ItemStack item = c.body.getInventory().getItem(potionSlot);
            if (item != null && ItemRoles.isThrowablePotion(item.getType()) && PotionKinds.of(item) == wanted) {
                return potionSlot;
            }
        }
        return Loadout.NONE;
    }

    @Override
    public boolean start(CombatContext c) {
        if (kind == null || !Loadout.has(slot)) {
            return false;
        }
        c.brain.lowerGuard();
        Hands.select(c.body, slot);
        c.npc.controller().stop();
        c.npc.controller().setAutoFace(false);
        startedAt = c.now;
        return true;
    }

    @Override
    public boolean tick(CombatContext c) {
        // Aim every tick while holding the item up.
        Vector eye = c.body.getEyeLocation().toVector();
        double yaw;
        double pitch;
        if (kind == Kind.SELF_HEAL) {
            yaw = c.body.getLocation().getYaw();
            pitch = 90.0;
        } else {
            Vector center = c.target.getLocation().toVector().add(new Vector(0, c.target.getHeight() * 0.5, 0));
            Vector to = center.subtract(eye);
            double gravity = kind == Kind.HARM_POTION ? Ballistics.POTION_GRAVITY : Ballistics.THROWN_GRAVITY;
            double speed = kind == Kind.HARM_POTION ? 0.9 : 1.2;
            Ballistics.Aim aim = Ballistics.solve(to.getX(), to.getY(), to.getZ(), speed, gravity, Ballistics.DRAG);
            yaw = aim.yaw() + Aim.error(c.settings.aimErrorDegrees);
            pitch = aim.pitch();
        }
        c.npc.controller().setLook((float) yaw, (float) pitch);
        if (c.now - startedAt < 3) {
            return true;
        }
        throwIt(c, yaw, pitch);
        return false;
    }

    private void throwIt(CombatContext c, double yaw, double pitch) {
        Loadout now = Loadout.scan(c.body.getInventory().getContents());
        int slotNow = switch (kind) {
            case HARM_POTION, SELF_HEAL -> slot;
            case FIRE_CHARGE -> now.fireCharge;
            case SNOWBALL -> now.snowball;
        };
        ItemStack stack = Loadout.has(slotNow) ? c.body.getInventory().getItem(slotNow) : null;
        if (stack == null) {
            return;
        }
        Vector direction = Aim.direction(yaw, pitch);
        c.body.swingMainHand();
        c.brain.trace("throws " + kind.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
        switch (kind) {
            case HARM_POTION, SELF_HEAL -> {
                ThrownPotion potion = c.body.launchProjectile(ThrownPotion.class,
                        kind == Kind.SELF_HEAL ? new Vector(0, -0.3, 0) : direction.multiply(0.9));
                ItemStack thrown = stack.clone();
                thrown.setAmount(1);
                potion.setItem(thrown);
            }
            case FIRE_CHARGE -> {
                SmallFireball fireball = c.body.launchProjectile(SmallFireball.class, direction.multiply(1.0));
                fireball.setIsIncendiary(false); // sets the target on fire, but does not set the world on fire
                fireball.setYield(0f);
            }
            case SNOWBALL -> {
                Projectile ball = stack.getType() == org.bukkit.Material.EGG
                        ? c.body.launchProjectile(Egg.class, direction.multiply(1.2))
                        : c.body.launchProjectile(Snowball.class, direction.multiply(1.2));
                ball.setShooter(c.body);
            }
        }
        Hands.consumeOne(c.body, slotNow);
        c.brain.cooldown(name(), c.now, 60 + ThreadLocalRandom.current().nextInt(40));
    }

    @Override
    public void end(CombatContext c) {
        c.npc.controller().setAutoFace(true);
        c.brain.dirty();
    }
}
