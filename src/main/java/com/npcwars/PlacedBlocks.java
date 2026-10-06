package com.npcwars;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Blocks NPCs place during a fight (cobwebs, water, lava) are temporary: they are put back after a few seconds, and
 * every one is restored when the plugin stops, so a fight never leaves the world changed.
 */
public final class PlacedBlocks {

    private record Placed(Block block, Material placed, Material original, long expireTick) {
    }

    private final List<Placed> placed = new ArrayList<>();

    /**
     * Places {@code type} at a block that is currently air or replaceable and schedules its removal.
     *
     * @return {@code false} if the spot was not free
     */
    public boolean place(Block block, Material type, long expireTick) {
        Material original = block.getType();
        if (!original.isAir() && original != Material.WATER && original != Material.SHORT_GRASS
                && original != Material.TALL_GRASS && original != Material.SNOW) {
            return false;
        }
        block.setType(type, false);
        placed.add(new Placed(block, type, original.isAir() ? Material.AIR : original, expireTick));
        return true;
    }

    public void tick(long tick) {
        if (placed.isEmpty()) {
            return;
        }
        for (Iterator<Placed> it = placed.iterator(); it.hasNext(); ) {
            Placed entry = it.next();
            if (tick >= entry.expireTick()) {
                restore(entry);
                it.remove();
            }
        }
    }

    /** Puts every block back right now. */
    public void restoreAll() {
        placed.forEach(PlacedBlocks::restore);
        placed.clear();
    }

    public int count() {
        return placed.size();
    }

    private static void restore(Placed entry) {
        if (entry.block().getType() == entry.placed()) {
            entry.block().setType(entry.original(), false);
        }
    }
}
