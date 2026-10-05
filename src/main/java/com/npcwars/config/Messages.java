package com.npcwars.config;

import com.npcwars.NpcWarsPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

/**
 * MiniMessage templates from the {@code messages} section of config.yml. Messages may use {@code <prefix>} and any
 * placeholder the caller passes in; placeholder values are inserted as plain text, so player- or admin-supplied
 * strings can never inject formatting.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final NpcWarsPlugin plugin;
    private final Map<String, String> single = new HashMap<>();
    private final Map<String, List<String>> lists = new HashMap<>();
    private final Set<String> reportedMissing = new HashSet<>();
    private String prefix = "";

    public Messages(NpcWarsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        single.clear();
        lists.clear();
        reportedMissing.clear();
        // The defaults bundled in the jar come first, so a config.yml written by an older version still has every key
        // that a newer version added; the server's own config.yml then overrides them.
        org.bukkit.configuration.Configuration defaults = plugin.getConfig().getDefaults();
        boolean any = false;
        if (defaults != null) {
            any |= load(defaults.getConfigurationSection("messages"));
        }
        any |= load(plugin.getConfig().getConfigurationSection("messages"));
        if (!any) {
            plugin.getLogger().warning("config.yml has no 'messages' section; message keys will show as placeholders.");
            return;
        }
        prefix = single.getOrDefault("prefix", "");
    }

    private boolean load(ConfigurationSection section) {
        if (section == null) {
            return false;
        }
        for (String key : section.getKeys(true)) {
            if (section.isList(key)) {
                lists.put(key, new ArrayList<>(section.getStringList(key)));
                single.remove(key);
            } else if (section.isString(key)) {
                single.put(key, section.getString(key));
                lists.remove(key);
            }
        }
        return true;
    }

    /** Builds a {@code <name>} placeholder whose value is inserted as plain, unformatted text. */
    public static TagResolver var(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /** Renders a message key to a component. Unknown keys render as a visible marker (and are logged once). */
    public Component render(String key, TagResolver... resolvers) {
        String template = single.get(key);
        if (template == null) {
            List<String> multi = lists.get(key);
            if (multi != null) {
                template = String.join("\n", multi);
            }
        }
        if (template == null) {
            if (reportedMissing.add(key)) {
                plugin.getLogger().warning("Missing message key '" + key + "' in config.yml");
            }
            return Component.text("<missing message: " + key + ">");
        }
        return MINI.deserialize(template, combine(resolvers));
    }

    /** Renders each line of a list-valued key (used for GUI lore). */
    public List<Component> renderList(String key, TagResolver... resolvers) {
        List<String> templates = lists.get(key);
        if (templates == null) {
            String one = single.get(key);
            templates = one == null ? List.of() : List.of(one);
        }
        TagResolver resolver = combine(resolvers);
        List<Component> out = new ArrayList<>(templates.size());
        for (String line : templates) {
            out.add(MINI.deserialize(line, resolver));
        }
        return out;
    }

    public void send(CommandSender target, String key, TagResolver... resolvers) {
        target.sendMessage(render(key, resolvers));
    }

    /** Sends a message to every online player allowed by {@code fight.announce}, plus the console. */
    public void announce(String key, TagResolver... resolvers) {
        Settings.Announce scope = plugin.settings().announce;
        if (scope == Settings.Announce.NONE) {
            return;
        }
        Component message = render(key, resolvers);
        Bukkit.getConsoleSender().sendMessage(message);
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (scope == Settings.Announce.ALL || player.hasPermission("npcplugin.notify")) {
                player.sendMessage(message);
            }
        }
    }

    /** Sends a message to the console and to online players who may run fights or get notifications. */
    public void notifyStaff(String key, TagResolver... resolvers) {
        Component message = render(key, resolvers);
        Bukkit.getConsoleSender().sendMessage(message);
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("npcplugin.notify") || player.hasPermission("npcplugin.fight")) {
                player.sendMessage(message);
            }
        }
    }

    private TagResolver combine(TagResolver... resolvers) {
        TagResolver.Builder builder = TagResolver.builder();
        builder.resolver(Placeholder.parsed("prefix", prefix));
        for (TagResolver resolver : resolvers) {
            builder.resolver(resolver);
        }
        return builder.build();
    }
}
