package com.npcwars.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Base class for the plugin's inventory menus. The holder identifies our inventories in the click/close events. */
public abstract class BaseGui implements InventoryHolder {

    protected Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Opens this menu for a player. */
    public abstract void open(Player player);

    /** Handles every click while this menu is the open top inventory. Implementations must cancel as needed. */
    public abstract void onClick(InventoryClickEvent event);

    /** Handles drags; the default cancels any drag that touches the menu itself. */
    public void onDrag(InventoryDragEvent event) {
        int size = inventory.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < size) {
                event.setCancelled(true);
                return;
            }
        }
    }

    public void onClose(InventoryCloseEvent event) {
    }
}
