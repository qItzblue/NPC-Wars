package com.npcwars.integration;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.npc.Npc;
import com.npcwars.npc.NpcSlot;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/**
 * Copies player-type Citizens NPCs into NPC-Wars NPCs (position, name, skin, armor and hands). The Citizens NPCs are not
 * touched. This is the only class that references the Citizens API, so it is only loaded when Citizens is present.
 */
public final class CitizensImporter {

    /** @param imported NPCs created; @param skipped not player NPCs, no location, or already imported before */
    public record Result(int imported, int skipped, boolean limitReached) {
    }

    private static final Pattern FORMATTING = Pattern.compile("(?i)[§&][0-9a-fk-or]|&#[0-9a-f]{6}");
    private static final Pattern PLAYER_NAME = Pattern.compile("\\w{1,16}");
    private static final Map<EquipmentSlot, NpcSlot> SLOTS = new EnumMap<>(EquipmentSlot.class);

    static {
        SLOTS.put(EquipmentSlot.HELMET, NpcSlot.HEAD);
        SLOTS.put(EquipmentSlot.CHESTPLATE, NpcSlot.CHEST);
        SLOTS.put(EquipmentSlot.LEGGINGS, NpcSlot.LEGS);
        SLOTS.put(EquipmentSlot.BOOTS, NpcSlot.FEET);
        SLOTS.put(EquipmentSlot.HAND, NpcSlot.MAIN_HAND);
        SLOTS.put(EquipmentSlot.OFF_HAND, NpcSlot.OFF_HAND);
    }

    private CitizensImporter() {
    }

    /**
     * Must run on the main thread.
     *
     * @param ids Citizens NPC ids to import, or {@code null} for every NPC
     */
    public static Result importNpcs(NpcWarsPlugin plugin, Set<Integer> ids) {
        int imported = 0;
        int skipped = 0;
        for (NPC source : CitizensAPI.getNPCRegistry()) {
            if (ids != null && !ids.contains(source.getId())) {
                continue;
            }
            Location at = source.getStoredLocation();
            if (at == null || at.getWorld() == null || !isPlayerNpc(source)) {
                skipped++;
                continue;
            }
            String label = FORMATTING.matcher(source.getRawName()).replaceAll("").strip();
            if (label.isEmpty()) {
                label = "Citizens " + source.getId();
            }
            if (alreadyImported(plugin, label, at)) {
                skipped++;
                continue;
            }
            if (plugin.npcs().count() >= plugin.settings().maxNpcs) {
                return new Result(imported, skipped, true);
            }
            Npc created = plugin.npcs().create(at.clone(), label, skinOf(source, label));
            Equipment equipment = source.getTraitNullable(Equipment.class);
            if (equipment != null) {
                for (Map.Entry<EquipmentSlot, NpcSlot> entry : SLOTS.entrySet()) {
                    ItemStack item = equipment.get(entry.getKey());
                    if (item != null && !item.getType().isAir()) {
                        plugin.npcs().setEquipment(created, entry.getValue(), item.clone());
                    }
                }
            }
            imported++;
        }
        return new Result(imported, skipped, false);
    }

    /** @return how many Citizens NPCs exist, for tab completion and messages */
    public static int count() {
        int total = 0;
        for (NPC ignored : CitizensAPI.getNPCRegistry()) {
            total++;
        }
        return total;
    }

    private static boolean isPlayerNpc(NPC npc) {
        MobType type = npc.getTraitNullable(MobType.class);
        return type == null || type.getType() == EntityType.PLAYER;
    }

    /** Citizens shows the skin of the player named like the NPC unless a skin is set, so do the same. */
    private static String skinOf(NPC npc, String label) {
        SkinTrait trait = npc.getTraitNullable(SkinTrait.class);
        if (trait != null && trait.getSkinName() != null && !trait.getSkinName().isBlank()) {
            return trait.getSkinName();
        }
        return PLAYER_NAME.matcher(label).matches() ? label : null;
    }

    private static boolean alreadyImported(NpcWarsPlugin plugin, String label, Location at) {
        for (Npc existing : plugin.npcs().all()) {
            Location home = existing.home();
            if (label.equals(existing.label()) && home.getWorld() == at.getWorld() && home.distanceSquared(at) < 1.0) {
                return true;
            }
        }
        return false;
    }
}
