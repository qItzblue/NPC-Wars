package com.npcwars.combat.brain;

import com.npcwars.combat.Ballistics;
import com.npcwars.combat.ItemRoles;
import com.npcwars.combat.Loadout;
import com.npcwars.config.Settings;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.util.Vector;

/**
 * Bows, crossbows and tridents. The NPC draws the weapon (the draw animation is real), aims with a lead on a moving
 * target and compensation for gravity, and releases after the draw time. The arrow or trident is launched by the plugin
 * (an NPC body has no client to release a bow), with the weapon's enchantments applied.
 */
final class RangedTactic implements Tactic {

    private enum Kind { BOW, CROSSBOW, TRIDENT }

    private Kind kind;
    private int weaponSlot = Loadout.NONE;
    private long startedAt;
    private int drawTicks;
    private double speed;

    @Override
    public String name() {
        return "ranged";
    }

    @Override
    public boolean enabled(Settings settings) {
        return settings.ranged;
    }

    @Override
    public double want(CombatContext c) {
        kind = null;
        if (!c.brain.ready(name(), c.now) || !c.sees || c.npc.controller().isAirborne()) {
            return 0;
        }
        Loadout l = c.loadout;
        double d = c.reach;
        boolean haveAmmo = Loadout.has(l.arrows);
        if (Loadout.has(l.crossbow) && haveAmmo && d >= 7 && d <= 40) {
            kind = Kind.CROSSBOW;
            weaponSlot = l.crossbow;
        } else if (Loadout.has(l.bow) && haveAmmo && d >= 7 && d <= 40) {
            kind = Kind.BOW;
            weaponSlot = l.bow;
        } else if (Loadout.has(l.trident) && d >= 5 && d <= 22 && l.trident != c.brain.bestMelee()) {
            kind = Kind.TRIDENT;
            weaponSlot = l.trident;
        } else if (Loadout.has(l.trident) && d >= 8 && d <= 22) {
            kind = Kind.TRIDENT;
            weaponSlot = l.trident;
        }
        if (kind == null) {
            return 0;
        }
        // With a melee weapon at hand an NPC shoots mostly from far away and closes in otherwise.
        double chance = Loadout.has(c.brain.bestMelee()) && d < 12 ? 0.35 : 1.0;
        return ThreadLocalRandom.current().nextDouble() < chance ? 60.0 : 0.0;
    }

    @Override
    public boolean start(CombatContext c) {
        if (kind == null || !Loadout.has(weaponSlot)) {
            return false;
        }
        c.brain.lowerGuard();
        Hands.select(c.body, weaponSlot);
        ItemStack weapon = c.body.getInventory().getItemInMainHand();
        switch (kind) {
            case BOW -> {
                drawTicks = 20;
                speed = 3.0;
            }
            case CROSSBOW -> {
                drawTicks = Math.max(5, 25 - 5 * weapon.getEnchantmentLevel(Enchantment.QUICK_CHARGE));
                speed = 3.15;
            }
            default -> {
                drawTicks = 10;
                speed = 2.5;
            }
        }
        c.npc.controller().stop();
        c.npc.controller().setAutoFace(false);
        c.body.startUsingItem(EquipmentSlot.HAND);
        startedAt = c.now;
        return true;
    }

    @Override
    public boolean tick(CombatContext c) {
        if (c.now - startedAt > 80 || !c.target.isValid() || c.target.isDead()) {
            return false;
        }
        Ballistics.Aim aim = aim(c);
        double yaw = aim.yaw() + Aim.error(c.settings.aimErrorDegrees);
        double pitch = aim.pitch() + Aim.error(c.settings.aimErrorDegrees * 0.5);
        c.npc.controller().setLook((float) yaw, (float) pitch);
        if (c.now - startedAt < drawTicks) {
            return true;
        }
        fire(c, yaw, pitch);
        return false;
    }

    /** Aims at where the target will be when the shot arrives, from the shooter's eyes. */
    private Ballistics.Aim aim(CombatContext c) {
        Vector eye = c.body.getEyeLocation().toVector();
        Vector center = c.target.getLocation().toVector().add(new Vector(0, c.target.getHeight() * 0.6, 0));
        double flightTicks = Math.min(40.0, center.distance(eye) / (speed * 0.9));
        Vector lead = c.target.getVelocity().clone().setY(0).multiply(flightTicks * 0.8);
        Vector to = center.add(lead).subtract(eye);
        double gravity = kind == Kind.BOW || kind == Kind.CROSSBOW || kind == Kind.TRIDENT ? Ballistics.ARROW_GRAVITY : Ballistics.THROWN_GRAVITY;
        return Ballistics.solve(to.getX(), to.getY(), to.getZ(), speed, gravity, Ballistics.DRAG);
    }

    private void fire(CombatContext c, double yaw, double pitch) {
        Loadout now = Loadout.scan(c.body.getInventory().getContents());
        ItemStack weapon = c.body.getInventory().getItemInMainHand();
        Vector velocity = Aim.direction(yaw, pitch).multiply(speed);
        c.body.swingMainHand();
        c.brain.trace("shoots its " + kind.name().toLowerCase(java.util.Locale.ROOT));
        if (kind == Kind.TRIDENT) {
            if (!ItemRoles.isTrident(weapon.getType())) {
                return;
            }
            Trident trident = c.body.launchProjectile(Trident.class, velocity);
            trident.setLoyaltyLevel(weapon.getEnchantmentLevel(Enchantment.LOYALTY));
            Hands.consumeOne(c.body, c.body.getInventory().getHeldItemSlot());
            c.body.getWorld().playSound(c.body.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 1.0f);
            return;
        }
        if (!Loadout.has(now.arrows)) {
            return;
        }
        ItemStack ammo = c.body.getInventory().getItem(now.arrows);
        AbstractArrow arrow;
        if (ammo.getType() == Material.SPECTRAL_ARROW) {
            arrow = c.body.launchProjectile(SpectralArrow.class, velocity);
        } else {
            Arrow plain = c.body.launchProjectile(Arrow.class, velocity);
            if (ammo.getType() == Material.TIPPED_ARROW && ammo.getItemMeta() instanceof PotionMeta meta
                    && meta.getBasePotionType() != null) {
                plain.setBasePotionType(meta.getBasePotionType());
            }
            arrow = plain;
        }
        arrow.setCritical(true);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        arrow.setWeapon(weapon.clone());
        boolean infinite = ammo.getType() == Material.ARROW && weapon.getEnchantmentLevel(Enchantment.INFINITY) > 0;
        if (!infinite) {
            Hands.consumeOne(c.body, now.arrows);
        }
        c.body.getWorld().playSound(c.body.getLocation(), kind == Kind.CROSSBOW ? Sound.ITEM_CROSSBOW_SHOOT : Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
    }

    @Override
    public void end(CombatContext c) {
        if (c.body.hasActiveItem()) {
            c.body.clearActiveItem();
        }
        c.npc.controller().setAutoFace(true);
        c.brain.cooldown(name(), c.now, ThreadLocalRandom.current().nextInt(30, 70));
        c.brain.dirty();
    }
}
