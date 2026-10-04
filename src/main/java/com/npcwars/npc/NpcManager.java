package com.npcwars.npc;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Settings;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

/**
 * Owns every {@link Npc}: creation, removal, spawning of the Mannequin bodies, equipment, persistence and the
 * per-tick maintenance that keeps bodies and records in sync. data.yml is the single source of truth, so bodies are
 * spawned non-persistent and re-created from the records whenever their chunk loads.
 */
public final class NpcManager {

    private static final int MAINTENANCE_INTERVAL_TICKS = 20;
    private static final int SPAWNS_PER_PASS = 40;

    private final NpcWarsPlugin plugin;
    private final NamespacedKey idKey;
    private final Map<Integer, Npc> npcs = new TreeMap<>();
    private final Map<UUID, Npc> byEntity = new HashMap<>();
    private final NpcSelection selection = new NpcSelection();
    private int nextId = 1;
    private long lastMaintenance;

    public NpcManager(NpcWarsPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "npc_id");
    }

    // ---------------------------------------------------------------- lookup

    public Collection<Npc> all() {
        return Collections.unmodifiableCollection(npcs.values());
    }

    public int count() {
        return npcs.size();
    }

    /** @return the NPC with this id, or {@code null} */
    public Npc get(int id) {
        return npcs.get(id);
    }

    /** @return the NPC whose body is this entity, or {@code null} for any other entity */
    public Npc byEntity(Entity entity) {
        if (!(entity instanceof Mannequin)) {
            return null;
        }
        return byEntity.get(entity.getUniqueId());
    }

    public NpcSelection selection() {
        return selection;
    }

    // ---------------------------------------------------------------- lifecycle of records

    /**
     * Creates, saves and spawns a new NPC.
     *
     * @throws IllegalStateException if {@code npc.max-npcs} is reached
     */
    public Npc create(Location home, String label, String skin) {
        if (npcs.size() >= plugin.settings().maxNpcs) {
            throw new IllegalStateException("NPC limit of " + plugin.settings().maxNpcs + " reached");
        }
        int id = nextId++;
        Npc npc = new Npc(plugin, id, label == null || label.isBlank() ? "NPC " + id : label, home);
        npc.setSkin(skin);
        npcs.put(npc.id(), npc);
        spawnBody(npc);
        plugin.data().requestSave();
        return npc;
    }

    /** Permanently deletes an NPC: body, record, team membership and selections. */
    public void remove(Npc npc) {
        plugin.fights().onNpcRemoved(npc);
        despawnBody(npc);
        npcs.remove(npc.id());
        plugin.teams().removeNpc(npc.id());
        selection.forget(npc.id());
        plugin.data().requestSave();
    }

    /** Deletes every NPC; returns how many there were. */
    public int removeAll() {
        int count = npcs.size();
        for (Npc npc : new ArrayList<>(npcs.values())) {
            remove(npc);
        }
        return count;
    }

    // ---------------------------------------------------------------- bodies

    /**
     * Spawns the body if it is missing and its chunk is loaded.
     *
     * @return {@code true} if the NPC has a live body afterwards
     */
    public boolean spawnBody(Npc npc) {
        return spawnBodyAt(npc, npc.home());
    }

    private boolean spawnBodyAt(Npc npc, Location where) {
        if (npc.isLive()) {
            return true;
        }
        if (where == null) {
            return false;
        }
        World world = where.getWorld();
        if (!world.isChunkLoaded(where.getBlockX() >> 4, where.getBlockZ() >> 4)) {
            return false;
        }
        forgetBody(npc);
        Mannequin body;
        try {
            body = world.spawn(where, Mannequin.class, spawned -> configure(npc, spawned));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not spawn NPC " + npc.id() + ": " + ex.getMessage());
            return false;
        }
        if (!body.isValid()) {
            plugin.getLogger().warning("NPC " + npc.id() + " was not spawned (another plugin cancelled the spawn?)");
            return false;
        }
        npc.setEntity(body);
        npc.setRespawnAtTick(0);
        byEntity.put(body.getUniqueId(), npc);
        npc.controller().reset();
        return true;
    }

    /** Removes the body but keeps the record. */
    public void despawnBody(Npc npc) {
        Mannequin body = npc.entity();
        npc.controller().stop();
        forgetBody(npc);
        if (body != null && body.isValid()) {
            body.remove();
        }
    }

    /** Called when the body died: drops the stale reference. */
    public void handleDeath(Npc npc) {
        forgetBody(npc);
        npc.controller().stop();
        npc.setRespawnAtTick(plugin.currentTick() + plugin.settings().idleRespawnDelayTicks);
    }

    private void forgetBody(Npc npc) {
        Mannequin old = npc.entity();
        if (old != null) {
            byEntity.remove(old.getUniqueId());
        }
        npc.setEntity(null);
    }

    private void configure(Npc npc, Mannequin body) {
        Settings settings = plugin.settings();
        body.setPersistent(false);
        body.getPersistentDataContainer().set(idKey, PersistentDataType.INTEGER, npc.id());
        body.setDescription(null);
        body.setImmovable(false);
        body.setInvulnerable(false);
        body.setCanPickupItems(false);
        AttributeInstance maxHealth = body.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(settings.maxHealth);
        }
        body.setHealth(Math.min(settings.maxHealth, maxHealth == null ? settings.maxHealth : maxHealth.getValue()));
        applyAppearance(npc, body);
        EntityEquipment equipment = body.getEquipment();
        for (NpcSlot slot : NpcSlot.values()) {
            equipment.setDropChance(slot.bukkit(), 0f);
            equipment.setItem(slot.bukkit(), npc.equipment(slot), true);
        }
    }

    /** Applies the label (if nametags are on) and the skin to a body, new or already spawned. */
    private void applyAppearance(Npc npc, Mannequin body) {
        Settings settings = plugin.settings();
        if (settings.showNametag && npc.label() != null && !npc.label().isBlank()) {
            body.customName(Component.text(npc.label()));
            body.setCustomNameVisible(true);
        } else {
            body.customName(null);
            body.setCustomNameVisible(false);
        }
        String skin = npc.skin() != null ? npc.skin() : settings.defaultSkin;
        if (skin != null && !skin.isBlank()) {
            body.setProfile(ResolvableProfile.resolvableProfile().name(skin).build());
        } else {
            body.setProfile(Mannequin.defaultProfile());
        }
    }

    /** Pushes the stored equipment onto the live body. */
    public void applyEquipment(Npc npc) {
        Mannequin body = npc.entity();
        if (body == null || !body.isValid()) {
            return;
        }
        EntityEquipment equipment = body.getEquipment();
        for (NpcSlot slot : NpcSlot.values()) {
            equipment.setItem(slot.bukkit(), npc.equipment(slot), true);
        }
    }

    /** Sets one equipment slot, updates the live body and saves. */
    public void setEquipment(Npc npc, NpcSlot slot, ItemStack item) {
        npc.setEquipment(slot, item);
        Mannequin body = npc.entity();
        if (body != null && body.isValid()) {
            body.getEquipment().setItem(slot.bukkit(), npc.equipment(slot), true);
        }
        plugin.data().requestSave();
    }

    /** Applies a full loadout in one go (used by kits). */
    public void setLoadout(Npc npc, Map<NpcSlot, ItemStack> loadout, boolean clearOthers) {
        if (clearOthers) {
            npc.clearEquipment();
        }
        loadout.forEach(npc::setEquipment);
        applyEquipment(npc);
        plugin.data().requestSave();
    }

    /** Changes the skin on the live body (health, target and position are untouched) and saves. */
    public void setSkin(Npc npc, String skin) {
        npc.setSkin(skin);
        refreshAppearance(npc);
        plugin.data().requestSave();
    }

    public void setLabel(Npc npc, String label) {
        npc.setLabel(label);
        refreshAppearance(npc);
        plugin.data().requestSave();
    }

    /** Moves the NPC (body and home position). */
    public void teleport(Npc npc, Location location) {
        npc.setHome(location);
        Mannequin body = npc.entity();
        if (body != null && body.isValid()) {
            body.teleport(location);
            body.setVelocity(new Vector());
        }
        plugin.data().requestSave();
    }

    /** Restores full health and clears fire, effects and fall damage. */
    public void heal(Npc npc) {
        Mannequin body = npc.entity();
        if (body == null || !body.isValid() || body.isDead()) {
            return;
        }
        AttributeInstance maxHealth = body.getAttribute(Attribute.MAX_HEALTH);
        body.setHealth(maxHealth == null ? plugin.settings().maxHealth : maxHealth.getValue());
        body.setFireTicks(0);
        body.setFallDistance(0f);
        body.setNoDamageTicks(0);
        for (PotionEffect effect : new ArrayList<>(body.getActivePotionEffects())) {
            body.removePotionEffect(effect.getType());
        }
    }

    private void refreshAppearance(Npc npc) {
        if (npc.isLive()) {
            applyAppearance(npc, npc.entity());
        }
    }

    // ---------------------------------------------------------------- ticking

    /** Runs every live NPC's movement controller (only those with something to do). */
    public void tickControllers(long tick) {
        for (Npc npc : npcs.values()) {
            try {
                if (npc.isLive() && npc.controller().needsTick()) {
                    npc.controller().tick(tick);
                }
            } catch (RuntimeException ex) {
                // One NPC in a strange state must not stop the others from moving.
                plugin.reportError("NPC movement", ex);
            }
        }
    }

    /** Re-creates missing bodies (chunk reloads, removed entities, delayed respawns). Throttled to every 2 seconds. */
    public void maintenance(long tick) {
        if (tick - lastMaintenance < MAINTENANCE_INTERVAL_TICKS) {
            return;
        }
        lastMaintenance = tick;
        spawnMissing(tick);
    }

    /**
     * Spawns bodies for NPCs whose home chunk has just loaded. The spawn happens one tick later because the chunk is
     * not fully usable while its load event is still being dispatched.
     */
    public void onChunkLoad(World world, int chunkX, int chunkZ) {
        if (npcs.isEmpty()) {
            return;
        }
        long tick = plugin.currentTick();
        List<Npc> waiting = null;
        for (Npc npc : npcs.values()) {
            if (npc.isLive() || npc.isSuppressed() || tick < npc.respawnAtTick()) {
                continue;
            }
            if (world.getName().equals(npc.worldName())
                    && ((int) Math.floor(npc.x())) >> 4 == chunkX && ((int) Math.floor(npc.z())) >> 4 == chunkZ) {
                if (waiting == null) {
                    waiting = new ArrayList<>();
                }
                waiting.add(npc);
            }
        }
        if (waiting != null) {
            List<Npc> toSpawn = waiting;
            Bukkit.getScheduler().runTask(plugin, () -> toSpawn.forEach(npc -> {
                if (npcs.get(npc.id()) == npc && !npc.isSuppressed()) {
                    spawnBody(npc);
                }
            }));
        }
    }

    /** Spawns bodies for NPCs whose world has just loaded. */
    public void onWorldLoad(World world) {
        for (Npc npc : npcs.values()) {
            if (!npc.isLive() && !npc.isSuppressed() && world.getName().equals(npc.worldName())) {
                spawnBody(npc);
            }
        }
    }

    private void spawnMissing(long tick) {
        int budget = SPAWNS_PER_PASS;
        for (Npc npc : npcs.values()) {
            if (budget <= 0) {
                return;
            }
            if (npc.isLive() || npc.isSuppressed() || tick < npc.respawnAtTick()) {
                continue;
            }
            if (spawnBody(npc)) {
                budget--;
            }
        }
    }

    // ---------------------------------------------------------------- persistence

    public void save(ConfigurationSection root) {
        root.set("next-id", nextId);
        root.set("npcs", null);
        ConfigurationSection section = root.createSection("npcs");
        for (Npc npc : npcs.values()) {
            ConfigurationSection entry = section.createSection(Integer.toString(npc.id()));
            entry.set("label", npc.label());
            entry.set("world", npc.worldName());
            entry.set("x", npc.x());
            entry.set("y", npc.y());
            entry.set("z", npc.z());
            entry.set("yaw", npc.yaw());
            entry.set("pitch", npc.pitch());
            if (npc.skin() != null) {
                entry.set("skin", npc.skin());
            }
            for (Map.Entry<NpcSlot, ItemStack> item : npc.equipmentView().entrySet()) {
                entry.set("equipment." + item.getKey().key(),
                        Base64.getEncoder().encodeToString(item.getValue().serializeAsBytes()));
            }
        }
    }

    /** Loads the records (no bodies are spawned yet; see {@link #onWorldLoad} / {@link #maintenance}). */
    public void load(ConfigurationSection root) {
        npcs.clear();
        byEntity.clear();
        ConfigurationSection section = root.getConfigurationSection("npcs");
        int highest = 0;
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                int id;
                try {
                    id = Integer.parseInt(key);
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("Ignoring NPC with invalid id '" + key + "' in data.yml");
                    continue;
                }
                if (entry == null || id <= 0) {
                    continue;
                }
                Npc npc = new Npc(plugin, id, entry.getString("label"),
                        new Location(null, entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"),
                                (float) entry.getDouble("yaw"), (float) entry.getDouble("pitch")));
                npc.setHome(entry.getString("world"), entry.getDouble("x"), entry.getDouble("y"),
                        entry.getDouble("z"), (float) entry.getDouble("yaw"), (float) entry.getDouble("pitch"));
                npc.setSkin(entry.getString("skin"));
                ConfigurationSection gear = entry.getConfigurationSection("equipment");
                if (gear != null) {
                    for (String slotKey : gear.getKeys(false)) {
                        NpcSlot slot = NpcSlot.fromKey(slotKey);
                        String encoded = gear.getString(slotKey);
                        if (slot == null || encoded == null) {
                            continue;
                        }
                        try {
                            npc.setEquipment(slot, ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
                        } catch (IllegalArgumentException | NullPointerException ex) {
                            plugin.getLogger().warning("NPC " + id + ": could not read " + slotKey + " item (" + ex.getMessage() + ")");
                        }
                    }
                }
                npcs.put(id, npc);
                highest = Math.max(highest, id);
            }
        }
        nextId = Math.max(root.getInt("next-id", 1), highest + 1);
    }

    /** Ids of all NPCs as strings, for tab completion. */
    public List<String> idStrings() {
        List<String> ids = new ArrayList<>(npcs.size());
        for (int id : npcs.keySet()) {
            ids.add(Integer.toString(id));
        }
        return ids;
    }

    /** Removes every body (plugin shutdown); records stay on disk. */
    public void shutdown() {
        for (Npc npc : npcs.values()) {
            npc.controller().stop();
            Mannequin body = npc.entity();
            if (body != null && body.isValid()) {
                body.remove();
            }
            npc.setEntity(null);
        }
        byEntity.clear();
    }
}
