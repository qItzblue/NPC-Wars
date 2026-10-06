package com.npcwars.kit;

import com.npcwars.NpcWarsPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Hook for EssentialsX kits. It reads Essentials' own {@code kits.yml} (falling back to the {@code kits:} section of
 * its config.yml) instead of calling its API: expanding a kit through the API can run the kit's command lines and
 * touch cooldowns, which must never happen when staff merely dress an NPC. Command lines are skipped; item lines are
 * parsed with {@link ItemParser}, so exotic Essentials-only item syntax is skipped with a console warning.
 * <p>
 * No Essentials class is referenced, so the hook cannot cause class-loading errors when Essentials is absent.
 */
public final class EssentialsKitProvider implements KitProvider {

    public static final String ID = "essentials";

    private final NpcWarsPlugin plugin;

    public EssentialsKitProvider(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "EssentialsX";
    }

    @Override
    public boolean isAvailable() {
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        return essentials != null && essentials.isEnabled();
    }

    @Override
    public List<Kit> kits() {
        List<Kit> out = new ArrayList<>();
        ConfigurationSection section = kitSection();
        if (section == null) {
            return out;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection kit = section.getConfigurationSection(name);
            Material icon = Material.CHEST;
            if (kit != null) {
                for (String line : kit.getStringList("items")) {
                    try {
                        icon = ItemParser.parse(line).getType();
                        break;
                    } catch (IllegalArgumentException ignored) {
                        // try the next line for an icon
                    }
                }
            }
            out.add(new Kit(ID, name.toLowerCase(Locale.ROOT), icon));
        }
        return out;
    }

    @Override
    public List<ItemStack> items(Kit kit, Player context) {
        List<ItemStack> items = new ArrayList<>();
        ConfigurationSection section = kitSection();
        if (section == null) {
            return items;
        }
        ConfigurationSection entry = null;
        for (String name : section.getKeys(false)) {
            if (name.equalsIgnoreCase(kit.id())) {
                entry = section.getConfigurationSection(name);
                break;
            }
        }
        if (entry == null) {
            return items;
        }
        for (String line : entry.getStringList("items")) {
            if (line.trim().startsWith("/")) {
                continue;
            }
            try {
                items.add(ItemParser.parse(line));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("EssentialsX kit '" + kit.id() + "': skipped '" + line + "' (" + ex.getMessage() + ")");
            }
        }
        return items;
    }

    private ConfigurationSection kitSection() {
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null) {
            return null;
        }
        File folder = essentials.getDataFolder();
        File kitsFile = new File(folder, "kits.yml");
        if (kitsFile.isFile()) {
            ConfigurationSection kits = YamlConfiguration.loadConfiguration(kitsFile).getConfigurationSection("kits");
            if (kits != null) {
                return kits;
            }
        }
        File config = new File(folder, "config.yml");
        return config.isFile() ? YamlConfiguration.loadConfiguration(config).getConfigurationSection("kits") : null;
    }
}
