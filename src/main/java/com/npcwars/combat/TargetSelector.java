package com.npcwars.combat;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Settings;
import com.npcwars.npc.Npc;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Keeps a per-world {@link SpatialGrid} of everything an NPC could attack (all living NPCs plus eligible players) and
 * answers "who is the nearest enemy of this side?". The grid is rebuilt every few ticks rather than per query.
 */
public final class TargetSelector {

    private static final double CELL_SIZE = 16.0;

    /** A snapshot of one potential target at refresh time. */
    public record Candidate(LivingEntity entity, int faction, double x, double y, double z) implements SpatialGrid.Point {
    }

    private final NpcWarsPlugin plugin;
    private final Map<UUID, SpatialGrid<Candidate>> grids = new HashMap<>();
    private long lastRefresh = Long.MIN_VALUE / 2;

    public TargetSelector(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return {@code true} if players of this kind can be attacked by NPCs right now */
    public boolean isEligible(Player player) {
        Settings settings = plugin.settings();
        if (!settings.targetPlayers || !player.isOnline() || player.isDead()) {
            return false;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SPECTATOR) {
            return false;
        }
        return !(mode == GameMode.CREATIVE && settings.ignoreCreativePlayers);
    }

    /** Rebuilds the grids if they are older than {@code fight.grid-refresh-ticks}. */
    public void refresh(long tick) {
        if (tick - lastRefresh < plugin.settings().gridRefreshTicks) {
            return;
        }
        lastRefresh = tick;
        grids.values().forEach(SpatialGrid::clear);
        Factions factions = plugin.factions();
        for (Npc npc : plugin.npcs().all()) {
            if (npc.isLive()) {
                add(npc.entity(), factions.of(npc));
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isEligible(player)) {
                add(player, factions.of(player));
            }
        }
    }

    private void add(LivingEntity entity, int faction) {
        SpatialGrid<Candidate> grid = grids.computeIfAbsent(entity.getWorld().getUID(), id -> new SpatialGrid<>(CELL_SIZE));
        grid.add(new Candidate(entity, faction, entity.getX(), entity.getY(), entity.getZ()));
    }

    /**
     * @param radius largest distance to search, or {@code <= 0} for no limit
     * @return the nearest living candidate that is not on {@code faction}, or {@code null}
     */
    public Candidate nearestEnemy(Entity self, int faction, double radius) {
        SpatialGrid<Candidate> grid = grids.get(self.getWorld().getUID());
        if (grid == null) {
            return null;
        }
        return grid.nearest(self.getX(), self.getY(), self.getZ(), radius,
                candidate -> candidate.faction() != faction && candidate.entity() != self && !candidate.entity().isDead());
    }

    public void clear() {
        grids.clear();
        lastRefresh = Long.MIN_VALUE / 2;
    }
}
