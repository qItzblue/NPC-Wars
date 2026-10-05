package com.npcwars.listener;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.npc.Npc;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEvent;

/**
 * Damage rules around NPCs: they are immune outside fights, team mates never hurt each other, and a hurt NPC gets a
 * short knockback window plus the chance to retaliate.
 */
public final class NpcDamageListener implements Listener {

    private final NpcWarsPlugin plugin;

    public NpcDamageListener(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Npc victim = plugin.npcs().byEntity(event.getEntity());
        boolean survival = victim != null && plugin.settings().lifeVulnerable
                && victim.behavior() == com.npcwars.npc.Behavior.LIFE;
        if (victim != null && !survival && plugin.settings().invulnerableWhenIdle && !plugin.fights().isRunning()) {
            switch (event.getCause()) {
                case VOID, KILL, SUICIDE -> { }
                default -> {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && !plugin.settings().friendlyFire) {
            Entity damager = shooterOrDamager(byEntity.getDamager());
            boolean npcInvolved = victim != null || plugin.npcs().byEntity(damager) != null;
            if (npcInvolved && damager != event.getEntity() && plugin.factions().allied(damager, event.getEntity())) {
                event.setCancelled(true);
            }
        }
    }

    /** Runs after other plugins had their say: only damage that really lands starts the knockback window. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        Npc victim = plugin.npcs().byEntity(event.getEntity());
        if (victim == null) {
            return;
        }
        victim.controller().onHurt(plugin.currentTick());
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            plugin.fights().onNpcDamaged(victim, shooterOrDamager(byEntity.getDamager()));
        }
    }

    /** NPCs must never wander through portals and leave the arena. */
    @EventHandler(ignoreCancelled = true)
    public void onPortal(EntityPortalEvent event) {
        if (plugin.npcs().byEntity(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    private static Entity shooterOrDamager(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }
}
