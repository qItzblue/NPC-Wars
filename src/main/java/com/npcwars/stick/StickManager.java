package com.npcwars.stick;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.npc.control.NpcController;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * The dupe stick: an item that turns players into NPCs. Select an area with two clicks, then one of three modes copies
 * the players standing in it, fills it with copies of you, or places a single copy of you. The copies carry the player's
 * skin, name and whole inventory, so they fight with the kit. A copy then either stands still or walks forward.
 *
 * <p>The stick shows its state in its own name and lore, so nothing is written to chat.
 */
public final class StickManager {

    private record Selection(String world, int[] first, int[] second) {
        Area area() {
            if (first == null || second == null) {
                return null;
            }
            return Area.of(first[0], first[1], first[2], second[0], second[1], second[2]);
        }
    }

    private record Walk(Vector direction, NpcController.Gait gait) {
    }

    private final NpcWarsPlugin plugin;
    private final NamespacedKey stickKey;
    private final NamespacedKey modeKey;
    private final NamespacedKey behaviorKey;
    private final Map<UUID, Selection> selections = new HashMap<>();
    /** NPCs that are walking forward, with the direction they walk in. */
    private final Map<Integer, Walk> walkers = new HashMap<>();

    public StickManager(NpcWarsPlugin plugin) {
        this.plugin = plugin;
        this.stickKey = new NamespacedKey(plugin, "dupe_stick");
        this.modeKey = new NamespacedKey(plugin, "dupe_mode");
        this.behaviorKey = new NamespacedKey(plugin, "dupe_behavior");
    }

    // ---------------------------------------------------------------- the item

    public ItemStack create(Player owner, StickMode mode, StickBehavior behavior) {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        meta.getPersistentDataContainer().set(stickKey, PersistentDataType.BYTE, (byte) 1);
        stick.setItemMeta(meta);
        write(stick, mode, behavior, selectionOf(owner));
        return stick;
    }

    public boolean isStick(ItemStack item) {
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(stickKey, PersistentDataType.BYTE);
    }

    public StickMode mode(ItemStack stick) {
        PersistentDataContainer data = stick.getItemMeta().getPersistentDataContainer();
        return StickMode.parse(data.get(modeKey, PersistentDataType.STRING), StickMode.COPY_PLAYERS);
    }

    public StickBehavior behavior(ItemStack stick) {
        PersistentDataContainer data = stick.getItemMeta().getPersistentDataContainer();
        return StickBehavior.parse(data.get(behaviorKey, PersistentDataType.STRING), StickBehavior.STAND);
    }

    /** Stores mode and behavior on the item and rewrites its name and lore to match. */
    public void write(ItemStack stick, StickMode mode, StickBehavior behavior, Selection selection) {
        ItemMeta meta = stick.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(modeKey, PersistentDataType.STRING, mode.name());
        data.set(behaviorKey, PersistentDataType.STRING, behavior.name());
        meta.displayName(Component.text("Dupe Stick", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        Area area = selection == null ? null : selection.area();
        List<Component> lore = new ArrayList<>();
        lore.add(line("Mode: ", mode.description(), NamedTextColor.YELLOW));
        lore.add(line("Then: ", behavior.description(), NamedTextColor.YELLOW));
        lore.add(line("Area: ", area == null ? "not set" : area.sizeX() + " x " + area.sizeY() + " x " + area.sizeZ() + " blocks",
                area == null ? NamedTextColor.RED : NamedTextColor.GREEN));
        lore.add(Component.empty());
        if (mode == StickMode.SINGLE) {
            lore.add(hint("Right-click a block or the ground: place a copy there"));
        } else {
            lore.add(hint("Left-click a block: corner 1"));
            lore.add(hint("Right-click a block: corner 2"));
            lore.add(hint("Right-click in the air: use"));
        }
        lore.add(hint("Sneak + left-click: change mode"));
        lore.add(hint("Sneak + right-click: stand / walk / march"));
        meta.lore(lore);
        stick.setItemMeta(meta);
    }

    private static Component line(String label, String value, NamedTextColor valueColor) {
        return Component.text(label, NamedTextColor.GRAY).append(Component.text(value, valueColor))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Component hint(String text) {
        return Component.text(text, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private Selection selectionOf(Player player) {
        return selections.get(player.getUniqueId());
    }

    private void refresh(Player player, ItemStack stick, StickMode mode, StickBehavior behavior) {
        write(stick, mode, behavior, selectionOf(player));
        player.getInventory().setItemInMainHand(stick);
    }

    // ---------------------------------------------------------------- clicks

    public void cycleMode(Player player, ItemStack stick) {
        refresh(player, stick, mode(stick).next(), behavior(stick));
    }

    public void toggleBehavior(Player player, ItemStack stick) {
        refresh(player, stick, mode(stick), behavior(stick).toggled());
    }

    public void setMode(Player player, ItemStack stick, StickMode mode) {
        refresh(player, stick, mode, behavior(stick));
    }

    public void setBehavior(Player player, ItemStack stick, StickBehavior behavior) {
        refresh(player, stick, mode(stick), behavior);
    }

    /** Sets corner 1 ({@code second == false}) or corner 2 of the player's area. */
    public void setCorner(Player player, ItemStack stick, Block block, boolean second) {
        Selection old = selectionOf(player);
        String world = block.getWorld().getName();
        int[] point = {block.getX(), block.getY(), block.getZ()};
        int[] keep = old != null && old.world().equals(world) ? (second ? old.first() : old.second()) : null;
        Selection updated = second ? new Selection(world, keep, point) : new Selection(world, point, keep);
        selections.put(player.getUniqueId(), updated);
        refresh(player, stick, mode(stick), behavior(stick));
        Area area = updated.area();
        if (area != null) {
            outline(player, area);
        }
    }

    public void clearSelection(Player player) {
        selections.remove(player.getUniqueId());
    }

    // ---------------------------------------------------------------- using it

    /**
     * Uses the stick in the player's hand.
     *
     * @return how many NPCs were created
     * @throws IllegalStateException with a message key and what to put in it if it cannot be used
     */
    public int use(Player player, ItemStack stick) {
        StickMode mode = mode(stick);
        StickBehavior behavior = behavior(stick);
        Selection selection = selectionOf(player);
        Area area = selection == null || !selection.world().equals(player.getWorld().getName()) ? null : selection.area();
        return switch (mode) {
            case COPY_PLAYERS -> {
                if (area == null) {
                    throw new StickException("stick.no-area");
                }
                yield copyPlayers(player, area, behavior);
            }
            case FILL_AREA -> {
                if (area == null) {
                    throw new StickException("stick.no-area");
                }
                yield fill(player, area, behavior);
            }
            case SINGLE -> single(player, behavior);
        };
    }

    private int copyPlayers(Player user, Area area, StickBehavior behavior) {
        int made = 0;
        for (Player candidate : new ArrayList<>(user.getWorld().getPlayers())) {
            if (plugin.npcs().byEntity(candidate) != null || candidate.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location at = candidate.getLocation();
            if (!area.contains(at.getX(), at.getY(), at.getZ())) {
                continue;
            }
            if (copy(candidate, at, behavior) == null) {
                throw new StickException("stick.limit", Messages.var("max", plugin.settings().maxNpcs), made);
            }
            made++;
        }
        if (made == 0) {
            throw new StickException("stick.nobody");
        }
        return made;
    }

    private int fill(Player user, Area area, StickBehavior behavior) {
        World world = user.getWorld();
        int limit = plugin.settings().stickMaxPerUse;
        int made = 0;
        for (double[] spot : area.gridSpots(plugin.settings().stickFillSpacing, limit)) {
            double y = groundAt(world, area, (int) Math.floor(spot[0]), (int) Math.floor(spot[1]));
            if (Double.isNaN(y)) {
                continue;
            }
            Location at = new Location(world, spot[0], y, spot[1], user.getLocation().getYaw(), 0f);
            if (copy(user, at, behavior) == null) {
                throw new StickException("stick.limit", Messages.var("max", plugin.settings().maxNpcs), made);
            }
            made++;
        }
        if (made == 0) {
            throw new StickException("stick.no-ground");
        }
        return made;
    }

    private int single(Player user, StickBehavior behavior) {
        double range = plugin.settings().stickRange;
        RayTraceResult hit = user.rayTraceBlocks(range);
        Location at;
        if (hit != null && hit.getHitBlock() != null) {
            Block block = hit.getHitBlock();
            // Stand on top of a block that was aimed at from above or the side; against a ceiling, stand below it.
            org.bukkit.block.BlockFace face = hit.getHitBlockFace();
            Block spot = face == null || face == org.bukkit.block.BlockFace.UP ? block.getRelative(org.bukkit.block.BlockFace.UP)
                    : block.getRelative(face);
            at = spot.getLocation().add(0.5, 0.0, 0.5);
        } else {
            // Aiming at the sky or something far away: use the ground below the point at the end of the line of sight.
            Vector far = user.getEyeLocation().toVector().add(user.getEyeLocation().getDirection().multiply(Math.min(range, 30.0)));
            World world = user.getWorld();
            int x = (int) Math.floor(far.getX());
            int z = (int) Math.floor(far.getZ());
            double y = Double.NaN;
            if (world.isChunkLoaded(x >> 4, z >> 4)) {
                for (int by = (int) Math.floor(far.getY()); by > Math.max(world.getMinHeight(), far.getY() - 16); by--) {
                    Block block = world.getBlockAt(x, by, z);
                    if (!block.isPassable() && world.getBlockAt(x, by + 1, z).isPassable() && world.getBlockAt(x, by + 2, z).isPassable()) {
                        y = by + 1.0;
                        break;
                    }
                }
            }
            if (Double.isNaN(y)) {
                throw new StickException("stick.no-target");
            }
            at = new Location(world, x + 0.5, y, z + 0.5);
        }
        at.setYaw(user.getLocation().getYaw());
        at.setPitch(0f);
        if (copy(user, at, behavior) == null) {
            throw new StickException("stick.limit", Messages.var("max", plugin.settings().maxNpcs), 0);
        }
        user.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1.0, 0), 8, 0.3, 0.5, 0.3, 0);
        return 1;
    }

    /** @return the y to stand at on the top solid block of a column inside the area, or NaN if there is none */
    private static double groundAt(World world, Area area, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return Double.NaN;
        }
        for (int y = area.maxY() + 1; y >= area.minY(); y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.isPassable() && world.getBlockAt(x, y + 1, z).isPassable() && world.getBlockAt(x, y + 2, z).isPassable()) {
                return y + 1.0;
            }
        }
        return Double.NaN;
    }

    // ---------------------------------------------------------------- copying a player

    /**
     * Makes an NPC that is a copy of the player: skin, name (unless {@code stick.copy-names} is off), team, and the
     * complete inventory, with the item the player holds moved to hotbar slot 0 so the NPC holds it too.
     *
     * @return the new NPC, or {@code null} if {@code npc.max-npcs} is reached
     */
    public Npc copy(Player source, Location at, StickBehavior behavior) {
        Npc npc;
        String label = plugin.settings().stickCopyNames ? source.getName() : plugin.pools().randomName();
        try {
            npc = plugin.npcs().create(at, label, source.getName());
        } catch (IllegalStateException ex) {
            return null;
        }
        ItemStack[] contents = source.getInventory().getContents();
        ItemStack[] copy = new ItemStack[Npc.INVENTORY_SIZE];
        for (int i = 0; i < copy.length && i < contents.length; i++) {
            copy[i] = contents[i];
        }
        int held = source.getInventory().getHeldItemSlot();
        if (held > 0 && held < 9) {
            ItemStack first = copy[0];
            copy[0] = copy[held];
            copy[held] = first;
        }
        // Never copy a dupe stick into an NPC.
        for (int i = 0; i < copy.length; i++) {
            if (isStick(copy[i])) {
                copy[i] = null;
            }
        }
        npc.setInventory(copy);
        plugin.npcs().applyInventory(npc);
        if (plugin.settings().stickCopyTeam) {
            int team = plugin.teams().teamOfPlayer(source.getUniqueId());
            if (team > 0) {
                plugin.teams().addNpc(team, npc.id());
            }
        }
        if (behavior != StickBehavior.STAND) {
            startWalking(npc, at.getYaw(), behavior == StickBehavior.MARCH ? NpcController.Gait.MARCH : NpcController.Gait.WALK);
        }
        plugin.data().requestSave();
        return npc;
    }

    // ---------------------------------------------------------------- walking forward

    public void startWalking(Npc npc, float yaw, NpcController.Gait gait) {
        double radians = Math.toRadians(yaw);
        Vector direction = new Vector(-Math.sin(radians), 0, Math.cos(radians));
        walkers.put(npc.id(), new Walk(direction, gait));
        npc.controller().walkDirection(direction, gait);
    }

    public boolean isWalking(Npc npc) {
        return walkers.containsKey(npc.id());
    }

    public void stopWalking(Npc npc) {
        if (walkers.remove(npc.id()) != null && npc.isLive()) {
            npc.controller().stop();
        }
    }

    /** Called when a fight starts (it takes control of everyone). */
    public void cancelWalks() {
        walkers.clear();
    }

    /** Keeps walkers walking (a body that respawned or was stopped by something else starts again). */
    public void tick(long tick) {
        if (walkers.isEmpty() || tick % 10 != 0) {
            return;
        }
        for (Iterator<Map.Entry<Integer, Walk>> it = walkers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Walk> entry = it.next();
            Npc npc = plugin.npcs().get(entry.getKey());
            if (npc == null) {
                it.remove();
            } else if (npc.isLive() && !npc.controller().isMoving()) {
                npc.controller().walkDirection(entry.getValue().direction(), entry.getValue().gait());
            }
        }
    }

    // ---------------------------------------------------------------- feedback without chat

    /** Draws the edges of the area with particles for a couple of seconds. */
    private void outline(Player player, Area area) {
        List<double[]> points = new ArrayList<>();
        double x1 = area.minX();
        double y1 = area.minY();
        double z1 = area.minZ();
        double x2 = area.maxX() + 1.0;
        double y2 = area.maxY() + 1.0;
        double z2 = area.maxZ() + 1.0;
        double step = Math.max(1.0, Math.max(Math.max(x2 - x1, y2 - y1), z2 - z1) / 40.0);
        for (double x = x1; x <= x2; x += step) {
            for (double y : new double[] {y1, y2}) {
                for (double z : new double[] {z1, z2}) {
                    points.add(new double[] {x, y, z});
                }
            }
        }
        for (double y = y1; y <= y2; y += step) {
            for (double x : new double[] {x1, x2}) {
                for (double z : new double[] {z1, z2}) {
                    points.add(new double[] {x, y, z});
                }
            }
        }
        for (double z = z1; z <= z2; z += step) {
            for (double x : new double[] {x1, x2}) {
                for (double y : new double[] {y1, y2}) {
                    points.add(new double[] {x, y, z});
                }
            }
        }
        World world = player.getWorld();
        int[] runs = {0};
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || runs[0]++ >= 8) {
                task.cancel();
                return;
            }
            for (double[] p : points) {
                player.spawnParticle(Particle.END_ROD, new Location(world, p[0], p[1], p[2]), 1, 0, 0, 0, 0);
            }
        }, 1L, 5L);
    }
}
