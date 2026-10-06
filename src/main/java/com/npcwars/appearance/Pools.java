package com.npcwars.appearance;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.npc.Npc;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The name and skin pools behind random NPC appearance, stored in {@code plugins/NPC-Wars/pools.yml} so they can be
 * edited by hand or with {@code /npcwars pool}. Skins are Minecraft player names (the server fetches the skin by name).
 */
public final class Pools {

    public enum Kind {
        NAMES("names"), SKINS("skins");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Kind parse(String text) {
            for (Kind kind : values()) {
                if (kind.key.equalsIgnoreCase(text) || kind.key.equalsIgnoreCase(text + "s")) {
                    return kind;
                }
            }
            return null;
        }
    }

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_ .\\-]{1,16}");
    private static final Pattern VALID_SKIN = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final NpcWarsPlugin plugin;
    private final File file;
    private List<String> names = List.of();
    private List<String> skins = List.of();

    public Pools(NpcWarsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pools.yml");
        if (!file.exists()) {
            plugin.saveResource("pools.yml", false);
        }
        reload();
    }

    public void reload() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        names = clean(yaml.getStringList("names"), false);
        skins = clean(yaml.getStringList("skins"), true);
    }

    public List<String> list(Kind kind) {
        return kind == Kind.NAMES ? names : skins;
    }

    public static boolean isValid(Kind kind, String value) {
        return value != null && (kind == Kind.NAMES ? VALID : VALID_SKIN).matcher(value).matches() && !value.isBlank();
    }

    /** @return {@code true} if added (false: invalid or already there) */
    public boolean add(Kind kind, String value) {
        if (!isValid(kind, value) || containsIgnoreCase(list(kind), value)) {
            return false;
        }
        List<String> updated = new ArrayList<>(list(kind));
        updated.add(value.trim());
        set(kind, updated);
        return true;
    }

    /** @return {@code true} if something was removed */
    public boolean remove(Kind kind, String value) {
        List<String> updated = new ArrayList<>(list(kind));
        boolean changed = updated.removeIf(entry -> entry.equalsIgnoreCase(value.trim()));
        if (changed) {
            set(kind, updated);
        }
        return changed;
    }

    /** @return a random name that no NPC currently uses as its label */
    public String randomName() {
        Set<String> taken = new HashSet<>();
        for (Npc npc : plugin.npcs().all()) {
            taken.add(npc.label());
        }
        return NamePicker.unique(names, taken, ThreadLocalRandom.current());
    }

    /** @return a random skin name, or {@code null} if the skin pool is empty */
    public String randomSkin() {
        return NamePicker.any(skins, ThreadLocalRandom.current());
    }

    private void set(Kind kind, List<String> values) {
        if (kind == Kind.NAMES) {
            names = List.copyOf(values);
        } else {
            skins = List.copyOf(values);
        }
        save();
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("Pools used for random NPC names and skins (edit by hand or with /npcwars pool).",
                "Skins are Minecraft player names; the server downloads the skin of that account."));
        yaml.set("names", names);
        yaml.set("skins", skins);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write pools.yml: " + ex.getMessage());
        }
    }

    private static List<String> clean(List<String> raw, boolean skin) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String entry : raw) {
            String value = entry == null ? "" : entry.trim();
            Kind kind = skin ? Kind.SKINS : Kind.NAMES;
            if (isValid(kind, value) && seen.add(value.toLowerCase(Locale.ROOT))) {
                out.add(value);
            }
        }
        return List.copyOf(out);
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        return list.stream().anyMatch(entry -> entry.equalsIgnoreCase(value.trim()));
    }
}
