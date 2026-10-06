package com.npcwars.npc;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.npc.control.NpcController;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * One NPC: the saved record (id, home position, skin, inventory) plus its live body, a Citizens player NPC, which may
 * be absent while the chunk is unloaded or after the NPC died. Main thread only.
 */
public final class Npc {

    private final int id;
    private final NpcController controller;
    /** Size of a player inventory: hotbar 0-8, storage 9-35, boots 36, leggings 37, chestplate 38, helmet 39, off hand 40. */
    public static final int INVENTORY_SIZE = 41;

    private final ItemStack[] inventory = new ItemStack[INVENTORY_SIZE];

    private String label;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String skin;
    private Behavior behavior = Behavior.STILL;

    private NPC citizen;
    private Player entity;
    /** While {@code true} the maintenance task will not respawn this NPC (it is waiting for a fight to end). */
    private boolean suppressed;
    /** Server tick at which an idle (non-fight) death may respawn; 0 when not waiting. */
    private long respawnAtTick;

    public Npc(NpcWarsPlugin plugin, int id, String label, Location home) {
        this.id = id;
        this.label = label;
        this.controller = new NpcController(plugin, this);
        setHome(home);
    }

    public int id() {
        return id;
    }

    public NpcController controller() {
        return controller;
    }

    /** Admin-facing label (shown above the NPC only when {@code npc.show-nametag} is on). */
    public String label() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String skin() {
        return skin;
    }

    public void setSkin(String skin) {
        this.skin = skin == null || skin.isBlank() ? null : skin;
    }

    public Behavior behavior() {
        return behavior;
    }

    public void setBehavior(Behavior behavior) {
        this.behavior = behavior == null ? Behavior.STILL : behavior;
    }

    public String worldName() {
        return worldName;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public void setHome(Location location) {
        this.worldName = location.getWorld() == null ? this.worldName : location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }

    /** Sets the position from raw values (used when loading data.yml, where the world may not be loaded yet). */
    public void setHome(String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /** @return the home world, or {@code null} if it is not loaded */
    public World world() {
        return worldName == null ? null : Bukkit.getWorld(worldName);
    }

    /** @return the home location, or {@code null} if the world is not loaded */
    public Location home() {
        World world = world();
        return world == null ? null : new Location(world, x, y, z, yaw, pitch);
    }

    /** @return a defensive copy of the item in an equipment slot, or {@code null} if empty */
    public ItemStack equipment(NpcSlot slot) {
        return item(slot.inventoryIndex());
    }

    /** Stores a copy of the item (or clears the slot for {@code null}/air). Does not touch the live entity. */
    public void setEquipment(NpcSlot slot, ItemStack item) {
        setItem(slot.inventoryIndex(), item);
    }

    /** @return the filled equipment slots (armor, hotbar slot 0 as the main hand, off hand) */
    public Map<NpcSlot, ItemStack> equipmentView() {
        Map<NpcSlot, ItemStack> view = new EnumMap<>(NpcSlot.class);
        for (NpcSlot slot : NpcSlot.values()) {
            ItemStack item = inventory[slot.inventoryIndex()];
            if (item != null) {
                view.put(slot, item.clone());
            }
        }
        return java.util.Collections.unmodifiableMap(view);
    }

    /** Empties the whole stored inventory (armor, hands, hotbar and storage). */
    public void clearEquipment() {
        Arrays.fill(inventory, null);
    }

    /** @return a defensive copy of one inventory slot (0-40), or {@code null} if empty */
    public ItemStack item(int index) {
        ItemStack item = inventory[index];
        return item == null ? null : item.clone();
    }

    /** Stores a copy of the item in an inventory slot (0-40), or clears it for {@code null}/air. */
    public void setItem(int index, ItemStack item) {
        inventory[index] = item == null || item.getType().isAir() ? null : item.clone();
    }

    /** @return copies of all 41 inventory slots; empty slots are {@code null} */
    public ItemStack[] inventorySnapshot() {
        ItemStack[] copy = new ItemStack[INVENTORY_SIZE];
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            copy[i] = inventory[i] == null ? null : inventory[i].clone();
        }
        return copy;
    }

    /** Replaces the stored inventory with copies of the given items (extra entries are ignored). */
    public void setInventory(ItemStack[] items) {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            setItem(i, items != null && i < items.length ? items[i] : null);
        }
    }

    /** @return the live body (a player entity owned by Citizens), or {@code null} if not spawned */
    public Player entity() {
        return entity;
    }

    /** @return the Citizens NPC behind the live body, or {@code null} */
    public NPC citizen() {
        return citizen;
    }

    void setBody(NPC citizen, Player entity) {
        this.citizen = citizen;
        this.entity = entity;
    }

    /** @return {@code true} if the body exists, is loaded and is alive */
    public boolean isLive() {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    public void setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
    }

    public long respawnAtTick() {
        return respawnAtTick;
    }

    public void setRespawnAtTick(long tick) {
        this.respawnAtTick = tick;
    }

    /** @return the body's current location, or the home location if the body is not spawned (may be {@code null}) */
    public Location currentLocation() {
        return isLive() ? entity.getLocation() : home();
    }
}
