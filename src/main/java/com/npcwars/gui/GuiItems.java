package com.npcwars.gui;

import com.npcwars.NpcWarsPlugin;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Builds the decorative and button items used in the menus, tagged so clicks can be recognised. */
public final class GuiItems {

    public static final String FILLER = "filler";
    public static final String PLACEHOLDER = "placeholder";

    private GuiItems() {
    }

    private static NamespacedKey key(NpcWarsPlugin plugin) {
        return new NamespacedKey(plugin, "gui_item");
    }

    /** Creates a menu item with a non-italic name and lore and a hidden id. */
    public static ItemStack item(NpcWarsPlugin plugin, Material material, Component name, List<Component> lore, String id) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(plain(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (Component line : lore) {
                lines.add(plain(line));
            }
            meta.lore(lines);
        }
        if (id != null) {
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, id);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** @return the hidden id of a menu item, or {@code null} for a normal item */
    public static String idOf(NpcWarsPlugin plugin, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
    }

    public static ItemStack filler(NpcWarsPlugin plugin) {
        return item(plugin, Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), null, FILLER);
    }

    private static Component plain(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
