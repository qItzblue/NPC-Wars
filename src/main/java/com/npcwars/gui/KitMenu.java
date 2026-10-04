package com.npcwars.gui;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Messages;
import com.npcwars.kit.Kit;
import com.npcwars.npc.Npc;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Paginated menu listing every kit from every available kit provider. Clicking a kit applies its armor and weapon to
 * the NPCs in the menu's {@link KitScope}; the menu stays open so several kits can be handed out in a row.
 */
public final class KitMenu extends BaseGui {

    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int CLOSE_SLOT = 50;
    private static final int NEXT_SLOT = 53;

    private final NpcWarsPlugin plugin;
    private final KitScope scope;
    private final List<Kit> kits;
    private int page;

    public KitMenu(NpcWarsPlugin plugin, Player viewer, KitScope scope) {
        this.plugin = plugin;
        this.scope = scope;
        this.kits = plugin.kits().allKits();
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.messages().render("gui.kits.title",
                Messages.var("scope", scope.describe())));
        render();
    }

    @Override
    public void open(Player player) {
        player.openInventory(inventory);
    }

    private int pageCount() {
        return Math.max(1, (kits.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void render() {
        Messages messages = plugin.messages();
        inventory.clear();
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < kits.size(); i++) {
            Kit kit = kits.get(start + i);
            Material icon = kit.icon() == null || kit.icon().isAir() ? Material.CHEST : kit.icon();
            inventory.setItem(i, GuiItems.item(plugin, icon,
                    messages.render("gui.kits.kit-name", Messages.var("kit", kit.id())),
                    messages.renderList("gui.kits.kit-lore", Messages.var("provider", plugin.kits().providerName(kit)),
                            Messages.var("scope", scope.describe())),
                    "kit:" + (start + i)));
        }
        for (int slot = PAGE_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiItems.filler(plugin));
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, GuiItems.item(plugin, Material.ARROW, messages.render("gui.kits.previous"), null, "prev"));
        }
        if (page < pageCount() - 1) {
            inventory.setItem(NEXT_SLOT, GuiItems.item(plugin, Material.ARROW, messages.render("gui.kits.next"), null, "next"));
        }
        inventory.setItem(INFO_SLOT, GuiItems.item(plugin, Material.BOOK,
                messages.render("gui.kits.info-name", Messages.var("page", page + 1), Messages.var("pages", pageCount())),
                messages.renderList("gui.kits.info-lore", Messages.var("scope", scope.describe()),
                        Messages.var("kits", kits.size())),
                "info"));
        inventory.setItem(CLOSE_SLOT, GuiItems.item(plugin, Material.BARRIER, messages.render("gui.kits.close"), null, "close"));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= SIZE) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        String id = GuiItems.idOf(plugin, inventory.getItem(raw));
        if (id == null) {
            return;
        }
        switch (id) {
            case "prev" -> {
                page = Math.max(0, page - 1);
                render();
            }
            case "next" -> {
                page = Math.min(pageCount() - 1, page + 1);
                render();
            }
            case "close" -> player.closeInventory();
            default -> {
                if (id.startsWith("kit:")) {
                    apply(player, kits.get(Integer.parseInt(id.substring(4))));
                }
            }
        }
    }

    private void apply(Player player, Kit kit) {
        Messages messages = plugin.messages();
        List<Npc> targets = scope.resolve(plugin, player);
        if (targets.isEmpty()) {
            messages.send(player, "kits.no-targets", Messages.var("scope", scope.describe()));
            return;
        }
        List<ItemStack> items = plugin.kits().itemsOf(kit, player);
        if (items.isEmpty()) {
            messages.send(player, "kits.empty-kit", Messages.var("kit", kit.id()), Messages.var("provider", plugin.kits().providerName(kit)));
            return;
        }
        int changed = plugin.kitApplier().apply(targets, items);
        messages.send(player, "kits.applied", Messages.var("kit", kit.id()),
                Messages.var("provider", plugin.kits().providerName(kit)), Messages.var("count", changed));
    }
}
