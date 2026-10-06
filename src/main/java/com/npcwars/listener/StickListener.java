package com.npcwars.listener;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.stick.StickException;
import com.npcwars.stick.StickManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Clicks with the dupe stick; see {@link StickManager} for what each click does. */
public final class StickListener implements Listener {

    private final NpcWarsPlugin plugin;

    public StickListener(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        StickManager stick = plugin.stick();
        ItemStack item = event.getItem();
        if (!stick.isStick(item)) {
            return;
        }
        event.setCancelled(true); // the stick never breaks, places or opens anything
        Player player = event.getPlayer();
        if (!player.hasPermission("npcplugin.stick")) {
            plugin.messages().sendAlways(player, "general.no-permission");
            return;
        }
        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (player.isSneaking()) {
            if (left) {
                stick.cycleMode(player, item);
            } else {
                stick.toggleBehavior(player, item);
            }
            return;
        }
        switch (action) {
            case LEFT_CLICK_BLOCK -> stick.setCorner(player, item, event.getClickedBlock(), false);
            case RIGHT_CLICK_BLOCK -> stick.setCorner(player, item, event.getClickedBlock(), true);
            case RIGHT_CLICK_AIR -> {
                try {
                    int made = stick.use(player, item);
                    plugin.messages().send(player, "stick.used", com.npcwars.config.Messages.var("count", made));
                } catch (StickException ex) {
                    plugin.messages().sendAlways(player, ex.messageKey(), ex.resolvers());
                }
            }
            default -> { }
        }
    }
}
