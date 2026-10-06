package com.npcwars.listener;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.npc.Npc;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;

/** Keeps NPC bodies in sync with the world: deaths, chunk loads, world loads and staff leaving. */
public final class NpcLifecycleListener implements Listener {

    private final NpcWarsPlugin plugin;

    public NpcLifecycleListener(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        Npc npc = plugin.npcs().byEntity(event.getEntity());
        if (npc == null) {
            return;
        }
        if (plugin.messages().debug()) {
            var cause = event.getEntity().getLastDamageCause();
            plugin.getLogger().info("[combat] NPC #" + npc.id() + " died: "
                    + (cause == null ? "unknown cause" : cause.getCause() + " (" + String.format(java.util.Locale.ROOT, "%.1f", cause.getFinalDamage()) + " damage)")
                    + (event.getEntity().getKiller() != null ? ", killer " + event.getEntity().getKiller().getName() : ""));
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (event instanceof org.bukkit.event.entity.PlayerDeathEvent playerDeath) {
            playerDeath.deathMessage(null); // an NPC's death is not announced in chat
            playerDeath.setKeepInventory(false);
        }
        plugin.runner().stop(npc);
        plugin.npcs().handleDeath(npc);
        plugin.fights().onNpcDeath(npc);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.npcs().onChunkLoad(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.npcs().onWorldLoad(event.getWorld());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.npcs().selection().clear(event.getPlayer().getUniqueId());
    }
}
