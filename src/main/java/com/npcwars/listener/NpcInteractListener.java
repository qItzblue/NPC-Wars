package com.npcwars.listener;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.gui.EquipmentGui;
import com.npcwars.npc.Npc;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Shift + Right-click on an NPC opens its equipment GUI. Any other interaction with an NPC is ignored. */
public final class NpcInteractListener implements Listener {

    /** A single right-click can reach us twice (interact-at and interact); this drops the echo. */
    private static final long DEBOUNCE_TICKS = 4;

    private final NpcWarsPlugin plugin;
    private final Map<UUID, Long> lastOpened = new HashMap<>();

    public NpcInteractListener(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        Npc npc = plugin.npcs().byEntity(event.getRightClicked());
        if (npc == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        if (!player.hasPermission("npcplugin.equip")) {
            plugin.messages().send(player, "general.no-permission");
            return;
        }
        long now = plugin.currentTick();
        Long last = lastOpened.get(player.getUniqueId());
        if (last != null && now - last < DEBOUNCE_TICKS) {
            return;
        }
        lastOpened.put(player.getUniqueId(), now);
        new EquipmentGui(plugin, npc).open(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastOpened.remove(event.getPlayer().getUniqueId());
    }
}
