package com.npcwars.listener;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.combat.ItemRoles;
import com.npcwars.combat.brain.CombatBrain;
import com.npcwars.combat.brain.Shields;
import com.npcwars.npc.Npc;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

/**
 * Makes up for what a Citizens player body does not get from the game on its own: raised shields blocking hits, wind
 * charge explosions pushing it, thrown ender pearls teleporting it, and keeps the explosions NPCs cause from breaking
 * blocks (unless {@code combat.explosions-break-blocks} is on).
 */
public final class CombatItemListener implements Listener {

    private final NpcWarsPlugin plugin;

    public CombatItemListener(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- shields

    /** A raised shield blocks a hit that comes from the front, like the game's 180 degree arc. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Npc victim = plugin.npcs().byEntity(event.getEntity());
        if (victim == null || !victim.isLive()) {
            return;
        }
        CombatBrain brain = plugin.fights().brainOf(victim);
        if (brain == null || !brain.isGuardUp(plugin.currentTick())) {
            return;
        }
        Entity attacker = event.getDamager();
        Entity source = attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
                ? shooter : attacker;
        Player body = victim.entity();
        Vector toAttacker = attacker.getLocation().toVector().subtract(body.getLocation().toVector()).setY(0);
        if (toAttacker.lengthSquared() > 1.0E-6 && toAttacker.normalize().dot(body.getLocation().getDirection().setY(0)) <= 0) {
            return; // hit from behind
        }
        event.setCancelled(true);
        brain.trace("blocks a hit with its shield (" + String.format(java.util.Locale.ROOT, "%.1f", event.getDamage()) + " damage stopped)");
        body.getWorld().playSound(body.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
        if (source instanceof LivingEntity livingSource && !(attacker instanceof Projectile)) {
            Vector push = livingSource.getLocation().toVector().subtract(body.getLocation().toVector()).setY(0);
            if (push.lengthSquared() > 1.0E-6) {
                livingSource.setVelocity(livingSource.getVelocity().add(push.normalize().multiply(0.3)));
            }
            // A real player's axe knocks the shield out; NPC axes do this themselves in AttackExecutor.
            if (livingSource instanceof Player player && plugin.npcs().byEntity(player) == null
                    && ItemRoles.isAxe(player.getInventory().getItemInMainHand().getType())) {
                Shields.disable(plugin, body, Shields.DISABLED_TICKS);
            }
        }
    }

    // ---------------------------------------------------------------- projectiles

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile instanceof EnderPearl pearl && pearl.getShooter() instanceof Player shooter
                && plugin.npcs().byEntity(shooter) != null) {
            // The game teleports a player at the pearl's landing spot; an NPC body has no client, so do it here.
            event.setCancelled(true);
            Location landing = pearl.getLocation();
            pearl.remove();
            Location destination = landing.clone();
            destination.setYaw(shooter.getLocation().getYaw());
            destination.setPitch(shooter.getLocation().getPitch());
            shooter.teleport(destination);
            shooter.setVelocity(new Vector());
            shooter.getWorld().playSound(destination, Sound.ENTITY_PLAYER_TELEPORT, 1.0f, 1.0f);
            double damage = plugin.settings().pearlDamage;
            if (damage > 0) {
                shooter.damage(damage, DamageSource.builder(DamageType.FALL).build());
            }
            return;
        }
        if (projectile instanceof WindCharge) {
            pushNpcBodies(projectile.getLocation());
        }
    }

    /**
     * A wind charge explosion shoves everything near it. Real players get that from the game; NPC bodies do not, so they
     * are pushed here, away from the blast and always a little up.
     */
    private void pushNpcBodies(Location blast) {
        double radius = plugin.settings().windChargeRadius;
        double boost = plugin.settings().windChargeBoost;
        if (boost <= 0.0) {
            return; // the game's own explosion knockback is used as it is
        }
        for (Npc npc : plugin.npcs().all()) {
            if (!npc.isLive() || npc.entity().getWorld() != blast.getWorld()) {
                continue;
            }
            Player body = npc.entity();
            Location center = body.getLocation().add(0, body.getHeight() / 2.0, 0);
            double distance = center.distance(blast);
            if (distance > radius) {
                continue;
            }
            Vector direction = center.toVector().subtract(blast.toVector());
            direction = direction.lengthSquared() < 1.0E-4 ? new Vector(0, 1, 0) : direction.normalize();
            direction.setY(Math.max(direction.getY(), 0.6));
            direction.normalize();
            double strength = boost * (1.0 - distance / radius * 0.6);
            body.setVelocity(direction.multiply(strength));
            if (plugin.messages().debug()) {
                plugin.getLogger().info(String.format(java.util.Locale.ROOT,
                        "[combat] wind charge at %.1f %.1f %.1f pushes NPC #%d (distance %.2f, strength %.2f)",
                        blast.getX(), blast.getY(), blast.getZ(), npc.id(), distance, strength));
            }
            npc.controller().onHurt(plugin.currentTick());
            npc.controller().cushionLanding(120);
        }
    }

    // ---------------------------------------------------------------- explosions

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (plugin.settings().explosionsBreakBlocks) {
            return;
        }
        Entity source = event.getEntity();
        boolean ours = plugin.explosives().remove(source.getUniqueId());
        if (!ours && source instanceof org.bukkit.entity.TNTPrimed tnt && tnt.getSource() instanceof Player owner
                && plugin.npcs().byEntity(owner) != null) {
            ours = true;
        }
        if (ours) {
            event.blockList().clear();
        }
    }
}
