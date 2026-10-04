package com.npcwars.gui;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.npc.NpcSlot;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The per-NPC equipment editor opened with Shift + Right-click. Six slots show what the NPC wears; items are moved in
 * and out by hand. Every click that touches the menu is cancelled and carried out manually, so items can never be
 * duplicated or placed in the wrong slot, and changes are applied to the NPC (and saved) immediately.
 */
public final class EquipmentGui extends BaseGui {

    private static final int SIZE = 27;
    private static final int INFO_SLOT = 4;
    private static final int KIT_SLOT = 21;
    private static final int CLEAR_SLOT = 22;
    private static final int HEAL_SLOT = 23;
    private static final Map<NpcSlot, Integer> SLOT_INDEX = new EnumMap<>(NpcSlot.class);

    static {
        SLOT_INDEX.put(NpcSlot.HEAD, 10);
        SLOT_INDEX.put(NpcSlot.CHEST, 11);
        SLOT_INDEX.put(NpcSlot.LEGS, 12);
        SLOT_INDEX.put(NpcSlot.FEET, 13);
        SLOT_INDEX.put(NpcSlot.MAIN_HAND, 15);
        SLOT_INDEX.put(NpcSlot.OFF_HAND, 16);
    }

    private final NpcWarsPlugin plugin;
    private final Npc npc;

    public EquipmentGui(NpcWarsPlugin plugin, Npc npc) {
        this.plugin = plugin;
        this.npc = npc;
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.messages().render("gui.equipment.title",
                Messages.var("id", npc.id())));
        refresh();
    }

    @Override
    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** Redraws the whole menu from the NPC's stored equipment. */
    public void refresh() {
        Messages messages = plugin.messages();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, GuiItems.filler(plugin));
        }
        for (Map.Entry<NpcSlot, Integer> entry : SLOT_INDEX.entrySet()) {
            inventory.setItem(entry.getValue(), displayFor(entry.getKey()));
        }
        String label = npc.label() == null || npc.label().isBlank() ? "-" : npc.label();
        inventory.setItem(INFO_SLOT, GuiItems.item(plugin, Material.NAME_TAG,
                messages.render("gui.equipment.info-name", Messages.var("id", npc.id())),
                messages.renderList("gui.equipment.info-lore", Messages.var("id", npc.id()), Messages.var("label", label),
                        Messages.var("team", teamText())),
                "info"));
        inventory.setItem(KIT_SLOT, GuiItems.item(plugin, Material.CHEST, messages.render("gui.equipment.kit-name"),
                messages.renderList("gui.equipment.kit-lore"), "kit"));
        inventory.setItem(CLEAR_SLOT, GuiItems.item(plugin, Material.BARRIER, messages.render("gui.equipment.clear-name"),
                messages.renderList("gui.equipment.clear-lore"), "clear"));
        inventory.setItem(HEAL_SLOT, GuiItems.item(plugin, Material.GOLDEN_APPLE, messages.render("gui.equipment.heal-name"),
                messages.renderList("gui.equipment.heal-lore"), "heal"));
    }

    private String teamText() {
        int team = plugin.teams().teamOfNpc(npc.id());
        return team == 0 ? "-" : Integer.toString(team);
    }

    private ItemStack displayFor(NpcSlot slot) {
        ItemStack equipped = npc.equipment(slot);
        if (equipped != null) {
            return equipped;
        }
        Material pane = slot.isArmor() ? Material.LIGHT_GRAY_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE;
        return GuiItems.item(plugin, pane, plugin.messages().render("gui.equipment.empty-" + slot.key()),
                plugin.messages().renderList("gui.equipment.empty-lore"), GuiItems.PLACEHOLDER);
    }

    private NpcSlot slotAt(int rawSlot) {
        for (Map.Entry<NpcSlot, Integer> entry : SLOT_INDEX.entrySet()) {
            if (entry.getValue() == rawSlot) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- clicks

    @Override
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (plugin.npcs().get(npc.id()) != npc) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < SIZE;

        if (inTop) {
            event.setCancelled(true);
            NpcSlot slot = slotAt(raw);
            if (slot != null) {
                editSlot(player, slot, event);
            } else {
                button(player, GuiItems.idOf(plugin, inventory.getItem(raw)));
            }
            resync(player);
            return;
        }

        // Clicks in the player's own inventory are normal, except the ones that could reach into the menu.
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            event.setCancelled(true);
            quickMove(player, event);
            resync(player);
        } else if (event.getClick() == ClickType.DOUBLE_CLICK || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
        }
    }

    private void button(Player player, String id) {
        if (id == null) {
            return;
        }
        switch (id) {
            case "kit" -> {
                if (player.hasPermission("npcplugin.kit")) {
                    // Swap menus one tick later: replacing an inventory inside its own click event can desync the client.
                    Bukkit.getScheduler().runTask(plugin, () -> new KitMenu(plugin, player, KitScope.single(npc.id())).open(player));
                } else {
                    plugin.messages().send(player, "general.no-permission");
                }
            }
            case "clear" -> {
                npc.clearEquipment();
                plugin.npcs().applyEquipment(npc);
                plugin.data().requestSave();
                refresh();
            }
            case "heal" -> {
                plugin.npcs().heal(npc);
                plugin.messages().send(player, "npc.healed", Messages.var("id", npc.id()));
            }
            default -> { }
        }
    }

    private void editSlot(Player player, NpcSlot slot, InventoryClickEvent event) {
        ItemStack current = npc.equipment(slot);
        ItemStack cursor = event.getCursor();
        boolean cursorEmpty = cursor == null || cursor.getType().isAir();

        switch (event.getClick()) {
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                if (current != null) {
                    if (player.getInventory().addItem(current).isEmpty()) {
                        setSlot(slot, null);
                    } else {
                        plugin.messages().send(player, "gui.equipment.inventory-full");
                    }
                }
            }
            case LEFT, RIGHT, DOUBLE_CLICK -> {
                if (cursorEmpty) {
                    if (current != null) {
                        player.setItemOnCursor(current);
                        setSlot(slot, null);
                    }
                    return;
                }
                if (!slot.accepts(cursor)) {
                    plugin.messages().send(player, "gui.equipment.wrong-slot");
                    return;
                }
                ItemStack placed = cursor.clone();
                placed.setAmount(1);
                if (current == null) {
                    setSlot(slot, placed);
                    player.setItemOnCursor(reduced(cursor));
                } else if (cursor.getAmount() == 1) {
                    setSlot(slot, placed);
                    player.setItemOnCursor(current);
                } else if (player.getInventory().addItem(current).isEmpty()) {
                    setSlot(slot, placed);
                    player.setItemOnCursor(reduced(cursor));
                } else {
                    plugin.messages().send(player, "gui.equipment.inventory-full");
                }
            }
            case NUMBER_KEY -> {
                int hotbar = event.getHotbarButton();
                if (hotbar < 0 || hotbar > 8) {
                    return;
                }
                ItemStack held = player.getInventory().getItem(hotbar);
                boolean heldEmpty = held == null || held.getType().isAir();
                if (heldEmpty) {
                    if (current != null) {
                        player.getInventory().setItem(hotbar, current);
                        setSlot(slot, null);
                    }
                    return;
                }
                if (!slot.accepts(held)) {
                    plugin.messages().send(player, "gui.equipment.wrong-slot");
                    return;
                }
                ItemStack placed = held.clone();
                placed.setAmount(1);
                if (current == null) {
                    setSlot(slot, placed);
                    player.getInventory().setItem(hotbar, reduced(held));
                } else if (held.getAmount() == 1) {
                    setSlot(slot, placed);
                    player.getInventory().setItem(hotbar, current);
                } else {
                    plugin.messages().send(player, "gui.equipment.inventory-full");
                }
            }
            default -> { }
        }
    }

    /** Shift-click on an item in the player's inventory: send one piece to its natural slot if that slot is free. */
    private void quickMove(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        Inventory source = event.getClickedInventory();
        if (clicked == null || clicked.getType().isAir() || source == null) {
            return;
        }
        NpcSlot slot = NpcSlot.preferredFor(clicked);
        if (npc.equipment(slot) != null) {
            plugin.messages().send(player, "gui.equipment.slot-occupied");
            return;
        }
        ItemStack placed = clicked.clone();
        placed.setAmount(1);
        setSlot(slot, placed);
        source.setItem(event.getSlot(), reduced(clicked));
    }

    private void setSlot(NpcSlot slot, ItemStack item) {
        plugin.npcs().setEquipment(npc, slot, item);
        inventory.setItem(SLOT_INDEX.get(slot), displayFor(slot));
    }

    /** @return the stack with one item fewer, or {@code null} if nothing is left */
    private static ItemStack reduced(ItemStack stack) {
        if (stack.getAmount() <= 1) {
            return null;
        }
        ItemStack copy = stack.clone();
        copy.setAmount(stack.getAmount() - 1);
        return copy;
    }

    private void resync(Player player) {
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    /** @return the NPC this menu edits */
    public Npc npc() {
        return npc;
    }
}
