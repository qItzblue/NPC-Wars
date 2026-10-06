package com.npcwars.npc;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Settings;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

/**
 * Owns every {@link Npc}: creation, removal, spawning of the Citizens player bodies, inventories, persistence and the
 * per-tick maintenance that keeps bodies and records in sync. data.yml is the single source of truth, so bodies are
 * spawned non-persistent and re-created from the records whenever their chunk loads.
 */
public final class NpcManager {

    private static final int MAINTENANCE_INTERVAL_TICKS = 20;
    private static final int SPAWNS_PER_PASS = 40;

    private final NpcWarsPlugin plugin;
    private final Map<Integer, Npc> npcs = new TreeMap<>();
    private final Map<UUID, Npc> byEntity = new HashMap<>();
    private final NpcSelection selection = new NpcSelection();
    private int nextId = 1;
    private long lastMaintenance;

    public NpcManager(NpcWarsPlugin plugin) {
        this.plugin = plugin;
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
        if (!(entity instanceof Player)) {
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
        npc.setBehavior(plugin.settings().lifeDefault ? Behavior.LIFE : Behavior.STILL);
        npcs.put(npc.id(), npc);
        spawnBody(npc);
        plugin.data().requestSave();
        return npc;
    }

    /**
     * Makes a copy of an NPC at a location: same name, skin, whole inventory, team and behavior.
     *
     * @throws IllegalStateException if {@code npc.max-npcs} is reached
     */
    public Npc duplicate(Npc source, Location at) {
        Npc copy = create(at, source.label(), source.skin());
        copy.setInventory(source.inventorySnapshot());
        copy.setBehavior(source.behavior());
        int team = plugin.teams().teamOfNpc(source.id());
        if (team > 0) {
            plugin.teams().addNpc(team, copy.id());
        }
        applyInventory(copy);
        plugin.data().requestSave();
        return copy;
    }

    /** Permanently deletes an NPC: body, record, team membership and selections. */
    public void remove(Npc npc) {
        plugin.fights().onNpcRemoved(npc);
        despawnBody(npc);
        npcs.remove(npc.id());
        plugin.teams().removeNpc(npc.id());
        selection.forget(npc.id());
        plugin.routeRunner().release(npc);
        plugin.stick().stopWalking(npc);
        plugin.life().release(npc);
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
        NPC citizen = CitizensAPI.getTemporaryNPCRegistry().createNPC(EntityType.PLAYER, bodyName(npc));
        try {
            prepare(npc, citizen);
            if (!citizen.spawn(where) || !citizen.isSpawned() || !(citizen.getEntity() instanceof Player body)) {
                citizen.destroy();
                plugin.getLogger().warning("NPC " + npc.id() + " was not spawned (another plugin cancelled the spawn?)");
                return false;
            }
            npc.setBody(citizen, body);
            configure(npc, body);
        } catch (RuntimeException ex) {
            citizen.destroy();
            npc.setBody(null, null);
            plugin.getLogger().warning("Could not spawn NPC " + npc.id() + ": " + ex);
            return false;
        }
        npc.setRespawnAtTick(0);
        byEntity.put(npc.entity().getUniqueId(), npc);
        npc.controller().reset();
        return true;
    }

    /** Removes the body but keeps the record. */
    public void despawnBody(Npc npc) {
        npc.controller().stop();
        forgetBody(npc);
    }

    /** Called when the body died: drops the stale reference. */
    public void handleDeath(Npc npc) {
        forgetBody(npc);
        npc.controller().stop();
        npc.setRespawnAtTick(plugin.currentTick() + plugin.settings().idleRespawnDelayTicks);
    }

    private void forgetBody(Npc npc) {
        Player old = npc.entity();
        NPC citizen = npc.citizen();
        if (old != null) {
            byEntity.remove(old.getUniqueId());
        }
        npc.setBody(null, null);
        if (citizen != null) {
            citizen.destroy(); // despawns the entity and takes it out of Citizens for good
        }
    }

    /** The name shown above the body; a player profile name is at most 16 characters. */
    private static String bodyName(Npc npc) {
        String label = npc.label() == null || npc.label().isBlank() ? "NPC " + npc.id() : npc.label();
        return label.length() > 16 ? label.substring(0, 16) : label;
    }

    /** Settings that must be in place before the body appears: name, skin, protection, nameplate. */
    private void prepare(Npc npc, NPC citizen) {
        Settings settings = plugin.settings();
        citizen.setProtected(false); // damage rules are ours (NpcDamageListener), not Citizens' blanket protection
        citizen.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, settings.showNametag);
        SkinTrait skin = citizen.getOrAddTrait(SkinTrait.class);
        String skinName = npc.skin() != null ? npc.skin() : settings.defaultSkin;
        if (skinName != null && !skinName.isBlank()) {
            skin.setSkinName(skinName);
        } else {
            skin.setFetchDefaultSkin(false); // no skin set: the default Steve/Alex, not the skin of an account named like the NPC
        }
    }

    private void configure(Npc npc, Player body) {
        Settings settings = plugin.settings();
        body.setInvulnerable(false);
        body.setCanPickupItems(false);
        body.setFoodLevel(20);
        body.setSaturation(20f);
        AttributeInstance maxHealth = body.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(settings.maxHealth);
        }
        body.setHealth(Math.min(settings.maxHealth, maxHealth == null ? settings.maxHealth : maxHealth.getValue()));
        applyInventory(npc);
    }

    /** Puts the stored inventory (armor, hands, hotbar and storage) onto the live body, replacing what it holds. */
    public void applyInventory(Npc npc) {
        Player body = npc.entity();
        if (body == null || !body.isValid()) {
            return;
        }
        PlayerInventory inventory = body.getInventory();
        inventory.clear();
        for (int i = 0; i < Npc.INVENTORY_SIZE; i++) {
            ItemStack item = npc.item(i);
            if (item != null) {
                inventory.setItem(i, item);
            }
        }
        inventory.setHeldItemSlot(0);
    }

    /**
     * Adds an item to an NPC: armor goes into its armor slot if that is free, a shield into the off hand, anything else
     * into the first free hotbar or storage slot. Saves and updates the live body.
     *
     * @return {@code false} if there was no room
     */
    public boolean giveItem(Npc npc, ItemStack item) {
        NpcSlot preferred = NpcSlot.preferredFor(item);
        int slot = -1;
        if (preferred != NpcSlot.MAIN_HAND && npc.item(preferred.inventoryIndex()) == null) {
            slot = preferred.inventoryIndex();
        } else {
            for (int i = 0; i < 36; i++) {
                if (npc.item(i) == null) {
                    slot = i;
                    break;
                }
            }
        }
        if (slot < 0) {
            return false;
        }
        npc.setItem(slot, item);
        Player body = npc.entity();
        if (body != null && body.isValid()) {
            body.getInventory().setItem(slot, npc.item(slot));
        }
        plugin.data().requestSave();
        return true;
    }

    /** Sets one equipment slot, updates the live body and saves. */
    public void setEquipment(Npc npc, NpcSlot slot, ItemStack item) {
        npc.setEquipment(slot, item);
        Player body = npc.entity();
        if (body != null && body.isValid()) {
            body.getInventory().setItem(slot.inventoryIndex(), npc.equipment(slot));
        }
        plugin.data().requestSave();
    }

    /**
     * Applies a kit: each entry is an inventory slot (0-40) and its item.
     *
     * @param clearOthers {@code true} to empty every other slot first
     */
    public void setLoadout(Npc npc, Map<Integer, ItemStack> loadout, boolean clearOthers) {
        if (clearOthers) {
            npc.clearEquipment();
        }
        loadout.forEach(npc::setItem);
        applyInventory(npc);
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
        Player body = npc.entity();
        if (body != null && body.isValid()) {
            body.teleport(location);
            body.setVelocity(new Vector());
        }
        plugin.data().requestSave();
    }

    /** Restores full health and clears fire, effects and fall damage. */
    public void heal(Npc npc) {
        Player body = npc.entity();
        if (body == null || !body.isValid() || body.isDead()) {
            return;
        }
        AttributeInstance maxHealth = body.getAttribute(Attribute.MAX_HEALTH);
        body.setHealth(maxHealth == null ? plugin.settings().maxHealth : maxHealth.getValue());
        body.setFoodLevel(20);
        body.setFireTicks(0);
        body.setFallDistance(0f);
        body.setNoDamageTicks(0);
        for (PotionEffect effect : new ArrayList<>(body.getActivePotionEffects())) {
            body.removePotionEffect(effect.getType());
        }
    }

    /**
     * Skin and name changes need a fresh body (Citizens reads them when the NPC spawns). The NPC reappears at the same
     * spot with the same health and inventory.
     */
    private void refreshAppearance(Npc npc) {
        if (!npc.isLive()) {
            return;
        }
        Player old = npc.entity();
        Location at = old.getLocation();
        double health = old.getHealth();
        ItemStack[] held = old.getInventory().getContents();
        forgetBody(npc);
        if (spawnBodyAt(npc, at) && npc.isLive()) {
            npc.entity().setHealth(Math.min(health, npc.entity().getHealth()));
            npc.entity().getInventory().setContents(held);
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
            if (npc.behavior() != Behavior.STILL) {
                entry.set("behavior", npc.behavior().name().toLowerCase(java.util.Locale.ROOT));
            }
            ItemStack[] stored = npc.inventorySnapshot();
            boolean any = false;
            for (int i = 0; i < stored.length; i++) {
                if (stored[i] == null) {
                    stored[i] = new ItemStack(org.bukkit.Material.AIR);
                } else {
                    any = true;
                }
            }
            if (any) {
                entry.set("inventory", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(stored)));
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
                npc.setBehavior(Behavior.parse(entry.getString("behavior"), Behavior.STILL));
                loadInventory(npc, entry);
                npcs.put(id, npc);
                highest = Math.max(highest, id);
            }
        }
        nextId = Math.max(root.getInt("next-id", 1), highest + 1);
    }

    /** Reads the inventory (current format) or the six equipment slots written by older versions. */
    private void loadInventory(Npc npc, ConfigurationSection entry) {
        String encoded = entry.getString("inventory");
        if (encoded != null) {
            try {
                npc.setInventory(ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded)));
            } catch (IllegalArgumentException | NullPointerException ex) {
                plugin.getLogger().warning("NPC " + npc.id() + ": could not read its inventory (" + ex.getMessage() + ")");
            }
            return;
        }
        ConfigurationSection gear = entry.getConfigurationSection("equipment");
        if (gear == null) {
            return;
        }
        for (String slotKey : gear.getKeys(false)) {
            NpcSlot slot = NpcSlot.fromKey(slotKey);
            String legacy = gear.getString(slotKey);
            if (slot == null || legacy == null) {
                continue;
            }
            try {
                npc.setEquipment(slot, ItemStack.deserializeBytes(Base64.getDecoder().decode(legacy)));
            } catch (IllegalArgumentException | NullPointerException ex) {
                plugin.getLogger().warning("NPC " + npc.id() + ": could not read " + slotKey + " item (" + ex.getMessage() + ")");
            }
        }
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
            forgetBody(npc);
        }
        byEntity.clear();
    }
}
