package com.npcwars.kit;

import com.npcwars.NpcWarsPlugin;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** The fallback kit system: kits live in the plugin's own kits.yml and always work, with or without other plugins. */
public final class BuiltInKitProvider implements KitProvider {

    public static final String ID = "builtin";
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private record Entry(Material icon, List<String> lines) {
    }

    private final NpcWarsPlugin plugin;
    private final File file;
    private final Map<String, Entry> kits = new TreeMap<>();

    public BuiltInKitProvider(NpcWarsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        reload();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Built-in";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /** Re-reads kits.yml (creating it with the default kits on first run). */
    public void reload() {
        if (!file.isFile()) {
            plugin.saveResource("kits.yml", false);
        }
        kits.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("kits");
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection kit = section.getConfigurationSection(name);
            if (kit == null) {
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            List<String> lines = kit.getStringList("items");
            Material icon = Material.matchMaterial(kit.getString("icon", ""));
            if (icon == null || !icon.isItem() || icon.isAir()) {
                icon = firstMaterial(lines);
            }
            kits.put(key, new Entry(icon, new ArrayList<>(lines)));
        }
    }

    @Override
    public List<Kit> kits() {
        List<Kit> out = new ArrayList<>();
        kits.forEach((name, entry) -> out.add(new Kit(ID, name, entry.icon())));
        return out;
    }

    @Override
    public List<ItemStack> items(Kit kit, Player context) {
        Entry entry = kits.get(kit.id());
        List<ItemStack> items = new ArrayList<>();
        if (entry == null) {
            return items;
        }
        for (String line : entry.lines()) {
            try {
                items.add(ItemParser.parse(line));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Built-in kit '" + kit.id() + "': skipped '" + line + "' (" + ex.getMessage() + ")");
            }
        }
        return items;
    }

    public boolean exists(String name) {
        return kits.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public List<String> names() {
        return new ArrayList<>(kits.keySet());
    }

    /** @return {@code true} if the text is a legal kit name (lower-case letters, digits, {@code _} and {@code -}) */
    public static boolean isValidName(String name) {
        return VALID_NAME.matcher(name.toLowerCase(Locale.ROOT)).matches();
    }

    /**
     * Saves a kit made from the given items and writes kits.yml.
     *
     * @return {@code false} if the file could not be written
     */
    public boolean create(String name, Material icon, List<ItemStack> items) {
        String key = name.toLowerCase(Locale.ROOT);
        List<String> lines = new ArrayList<>();
        for (ItemStack item : items) {
            lines.add("base64:" + Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        kits.put(key, new Entry(icon, lines));
        return write();
    }

    /** @return {@code true} if a kit with that name existed and was removed and the file was saved */
    public boolean delete(String name) {
        if (kits.remove(name.toLowerCase(Locale.ROOT)) == null) {
            return false;
        }
        return write();
    }

    private boolean write() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("kits", null);
        for (Map.Entry<String, Entry> kit : kits.entrySet()) {
            String path = "kits." + kit.getKey();
            yaml.set(path + ".icon", kit.getValue().icon().name());
            yaml.set(path + ".items", kit.getValue().lines());
        }
        try {
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save kits.yml: " + ex.getMessage());
            return false;
        }
    }

    private static Material firstMaterial(List<String> lines) {
        for (String line : lines) {
            try {
                return ItemParser.parse(line).getType();
            } catch (IllegalArgumentException ignored) {
                // try the next line
            }
        }
        return Material.CHEST;
    }
}
