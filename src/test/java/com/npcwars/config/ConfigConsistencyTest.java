package com.npcwars.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Keeps config.yml and the code in sync: every message key the code sends must exist, every message must render
 * without leftover MiniMessage tags, and the shipped YAML files must parse.
 */
class ConfigConsistencyTest {

    private static final Pattern KEY = Pattern.compile(
            "\"((?:general|npc|team|fight|kits|massaction|action|help|status|gui|deps|import|appearance|life|route|ai|debug)\\.[a-z0-9.\\-]+)\"");
    private static final Set<String> MINIMESSAGE_TAGS = Set.of("prefix", "newline", "reset", "bold", "red", "green",
            "yellow", "gold", "gray", "white", "dark_gray");

    private static YamlConfiguration config;
    private static final Map<String, List<String>> MESSAGES = new TreeMap<>();

    @BeforeAll
    static void load() {
        config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        ConfigurationSection section = config.getConfigurationSection("messages");
        assertNotNull(section, "config.yml needs a messages section");
        for (String key : section.getKeys(true)) {
            if (section.isList(key)) {
                MESSAGES.put(key, section.getStringList(key));
            } else if (section.isString(key)) {
                MESSAGES.put(key, List.of(section.getString(key)));
            }
        }
    }

    private static Set<String> keysUsedInCode() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Matcher matcher = KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }
        return keys;
    }

    @Test
    void everyMessageKeyUsedInCodeExists() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String key : keysUsedInCode()) {
            boolean isMessage = MESSAGES.containsKey(key);
            boolean isSetting = config.contains(key);
            boolean isFilename = key.equals("kits.yml");
            boolean isDynamicPrefix = key.equals("gui.equipment.empty-");
            if (!isMessage && !isSetting && !isFilename && !isDynamicPrefix) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "message keys used in code but missing from config.yml: " + missing);
    }

    @Test
    void messageKeysAreNotYamlBooleans() {
        // "on"/"off"/"yes"/"no" as a key are read as true/false by YAML 1.1, so such a message could never be found.
        for (String key : MESSAGES.keySet()) {
            for (String part : key.split("\\.")) {
                assertFalse(Set.of("true", "false").contains(part), "message key " + key + " has a boolean-looking part");
            }
        }
    }

    @Test
    void equipmentSlotPlaceholderMessagesExist() {
        for (String slot : List.of("head", "chest", "legs", "feet", "main-hand", "off-hand")) {
            assertTrue(MESSAGES.containsKey("gui.equipment.empty-" + slot), "missing gui.equipment.empty-" + slot);
        }
    }

    @Test
    void everyMessageRendersWithoutLeftoverTags() {
        MiniMessage mini = MiniMessage.miniMessage();
        for (Map.Entry<String, List<String>> entry : MESSAGES.entrySet()) {
            for (String template : entry.getValue()) {
                TagResolver.Builder resolvers = TagResolver.builder();
                resolvers.resolver(Placeholder.parsed("prefix", "[P] "));
                Matcher tags = Pattern.compile("<([a-z_]+)>").matcher(template);
                while (tags.find()) {
                    if (!MINIMESSAGE_TAGS.contains(tags.group(1))) {
                        resolvers.resolver(Placeholder.unparsed(tags.group(1), "X"));
                    }
                }
                Component rendered = mini.deserialize(template, resolvers.build());
                String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
                assertFalse(plain.contains("<"), entry.getKey() + " contains an unparsed tag: " + plain);
            }
        }
    }

    @Test
    void messagesDoNotLeakTeamNamesIntoGameChat() {
        // Only the admin commands may show a team's name; announcements and GUI texts must not even have the slot.
        for (Map.Entry<String, List<String>> entry : MESSAGES.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("fight.") || key.startsWith("gui.")) {
                for (String template : entry.getValue()) {
                    assertFalse(template.contains("<name>"), key + " must not print a team name");
                }
            }
        }
    }

    @Test
    void defaultSettingsAreSane() {
        assertEquals("RESPAWN_ON_FIGHT_END", config.getString("fight.on-death"));
        assertEquals("FREE_FOR_ALL", config.getString("fight.unteamed-npcs"));
        assertTrue(config.getBoolean("npc.invulnerable-when-idle"));
        assertTrue(config.getBoolean("npc.show-nametag"), "names are shown by default so random names are visible");
        assertFalse(config.getBoolean("fight.friendly-fire"));
        assertEquals(List.of(60, 30, 10, 5, 4, 3, 2, 1), config.getIntegerList("fight.countdown-marks"));
    }

    @Test
    void shippedKitsFileParses() {
        YamlConfiguration kits = YamlConfiguration.loadConfiguration(new File("src/main/resources/kits.yml"));
        ConfigurationSection section = kits.getConfigurationSection("kits");
        assertNotNull(section);
        assertFalse(section.getKeys(false).isEmpty());
        for (String name : section.getKeys(false)) {
            assertFalse(section.getStringList(name + ".items").isEmpty(), "kit " + name + " has no items");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void pluginYmlDeclaresEveryCommandAndPermissionTheCodeUses() throws IOException {
        // Raw SnakeYAML on purpose: Bukkit's YamlConfiguration would split "npcplugin.admin" at the dot.
        Map<String, Object> plugin;
        try (java.io.Reader reader = Files.newBufferedReader(Path.of("src/main/resources/plugin.yml"), StandardCharsets.UTF_8)) {
            plugin = new org.yaml.snakeyaml.Yaml().load(reader);
        }
        Map<String, Object> commands = (Map<String, Object>) plugin.get("commands");
        for (String command : List.of("npcwars", "kitall", "massaction")) {
            assertTrue(commands.containsKey(command), "plugin.yml lacks command " + command);
        }
        Set<String> declared = ((Map<String, Object>) plugin.get("permissions")).keySet();
        Pattern permission = Pattern.compile("\"(npcplugin\\.[a-z.*]+)\"");
        Set<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Matcher matcher = permission.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    used.add(matcher.group(1));
                }
            }
        }
        used.removeAll(declared);
        assertTrue(used.isEmpty(), "permissions used in code but not declared in plugin.yml: " + used);
    }
}
