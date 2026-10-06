package com.npcwars.kit;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Parses one-line item descriptions used by kits:
 * <pre>
 *   DIAMOND_SWORD sharpness:3 unbreaking:2
 *   iron_chestplate 1 enchant:protection:4 name:&amp;6Shiny_Plate
 *   SPLASH_POTION potion:harming
 *   base64:&lt;serialized item&gt;
 * </pre>
 * The syntax is a superset of the EssentialsX kit item format for the parts that matter (item, amount, enchantments,
 * name, lore). Commands (lines starting with {@code /}) are rejected.
 */
public final class ItemParser {

    /** Legacy enchantment names still used in older kit files. */
    private static final Map<String, String> LEGACY_ENCHANTS = Map.ofEntries(
            Map.entry("damage_all", "sharpness"),
            Map.entry("damage_undead", "smite"),
            Map.entry("damage_arthropods", "bane_of_arthropods"),
            Map.entry("protection_environmental", "protection"),
            Map.entry("protection_fire", "fire_protection"),
            Map.entry("protection_fall", "feather_falling"),
            Map.entry("protection_explosions", "blast_protection"),
            Map.entry("protection_projectile", "projectile_protection"),
            Map.entry("durability", "unbreaking"),
            Map.entry("dig_speed", "efficiency"),
            Map.entry("arrow_damage", "power"),
            Map.entry("arrow_knockback", "punch"),
            Map.entry("arrow_fire", "flame"),
            Map.entry("arrow_infinite", "infinity"),
            Map.entry("loot_bonus_mobs", "looting"),
            Map.entry("loot_bonus_blocks", "fortune"),
            Map.entry("oxygen", "respiration"),
            Map.entry("water_worker", "aqua_affinity"),
            Map.entry("thorns", "thorns"),
            Map.entry("knockback", "knockback"),
            Map.entry("fire_aspect", "fire_aspect"),
            Map.entry("sweeping", "sweeping_edge"));

    private ItemParser() {
    }

    /**
     * @throws IllegalArgumentException with a readable reason if the line cannot be understood
     */
    public static ItemStack parse(String line) {
        String text = line == null ? "" : line.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty item line");
        }
        if (text.startsWith("/")) {
            throw new IllegalArgumentException("commands are not supported in kits: " + text);
        }
        if (text.regionMatches(true, 0, "base64:", 0, 7)) {
            try {
                return ItemStack.deserializeBytes(Base64.getDecoder().decode(text.substring(7).trim()));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("unreadable base64 item");
            }
        }

        Material material = null;
        int amount = 1;
        List<String[]> enchants = new ArrayList<>();
        String name = null;
        String potion = null;
        List<String> lore = new ArrayList<>();

        for (String token : text.split("\\s+")) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (token.chars().allMatch(Character::isDigit)) {
                amount = Math.max(1, Math.min(99, Integer.parseInt(token)));
            } else if (lower.startsWith("name:")) {
                name = token.substring(5).replace('_', ' ');
            } else if (lower.startsWith("potion:")) {
                potion = lower.substring(7).replace("minecraft:", "");
            } else if (lower.startsWith("lore:")) {
                for (String part : token.substring(5).split("\\|")) {
                    lore.add(part.replace('_', ' '));
                }
            } else if (lower.startsWith("enchant:")) {
                String[] parts = token.substring(8).split(":");
                enchants.add(new String[] {parts[0], parts.length > 1 ? parts[1] : "1"});
            } else if (token.contains(":") && material != null) {
                String[] parts = token.split(":");
                enchants.add(new String[] {parts[0], parts.length > 1 ? parts[1] : "1"});
            } else if (material == null) {
                material = Material.matchMaterial(token);
                if (material == null || !material.isItem()) {
                    throw new IllegalArgumentException("unknown item '" + token + "'");
                }
            } else {
                throw new IllegalArgumentException("unexpected '" + token + "'");
            }
        }
        if (material == null) {
            throw new IllegalArgumentException("no item name in '" + text + "'");
        }

        ItemStack stack = new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
        for (String[] enchant : enchants) {
            Enchantment enchantment = findEnchantment(enchant[0]);
            if (enchantment == null) {
                throw new IllegalArgumentException("unknown enchantment '" + enchant[0] + "'");
            }
            int level;
            try {
                level = Math.max(1, Math.min(255, Integer.parseInt(enchant[1])));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("bad enchantment level '" + enchant[1] + "'");
            }
            stack.addUnsafeEnchantment(enchantment, level);
        }
        if (potion != null) {
            if (!(stack.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta meta)) {
                throw new IllegalArgumentException("potion: only works on potions and tipped arrows");
            }
            org.bukkit.potion.PotionType type = RegistryAccess.registryAccess().getRegistry(RegistryKey.POTION)
                    .get(NamespacedKey.minecraft(potion));
            if (type == null) {
                throw new IllegalArgumentException("unknown potion '" + potion + "' (examples: healing, strong_healing, harming, strength, swiftness)");
            }
            meta.setBasePotionType(type);
            stack.setItemMeta(meta);
        }
        if (name != null || !lore.isEmpty()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
                if (name != null) {
                    meta.displayName(legacy.deserialize(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                }
                if (!lore.isEmpty()) {
                    List<Component> lines = new ArrayList<>();
                    for (String l : lore) {
                        lines.add(legacy.deserialize(l).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                    }
                    meta.lore(lines);
                }
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    private static Enchantment findEnchantment(String name) {
        String key = name.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        key = LEGACY_ENCHANTS.getOrDefault(key, key);
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(NamespacedKey.minecraft(key));
    }
}
