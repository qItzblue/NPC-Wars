package com.npcwars.path;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;

/**
 * {@link Terrain} backed by a live world. Block lookups are cached until {@link #clearCache()} (the {@link PathService}
 * clears it every few seconds), and unloaded chunks are reported as {@link Terrain#UNLOADED} instead of being loaded.
 * Main thread only.
 */
public final class BukkitTerrain implements Terrain {

    private static final byte MISSING = -1;
    private static final int MAX_CACHE_ENTRIES = 400_000;
    /** Blocks with a collision top at or below this height can be stepped onto without jumping. */
    private static final double STEP_HEIGHT = 0.6;

    private static final Set<Material> HAZARDS = EnumSet.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.COBWEB, Material.POWDER_SNOW,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POINTED_DRIPSTONE);
    private static final Set<Material> WATER_PLANTS = EnumSet.of(
            Material.WATER, Material.KELP, Material.KELP_PLANT, Material.SEAGRASS, Material.TALL_SEAGRASS,
            Material.BUBBLE_COLUMN);

    private final World world;
    private final Long2ByteOpenHashMap cache = new Long2ByteOpenHashMap();

    BukkitTerrain(World world) {
        this.world = world;
        this.cache.defaultReturnValue(MISSING);
    }

    public World world() {
        return world;
    }

    void clearCache() {
        cache.clear();
        cache.trim();
    }

    @Override
    public int type(int x, int y, int z) {
        if (y < world.getMinHeight()) {
            return HAZARD_VOID;
        }
        if (y >= world.getMaxHeight()) {
            return AIR;
        }
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return UNLOADED;
        }
        long key = ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) (y + 2048) & 0xFFFL);
        byte cached = cache.get(key);
        if (cached != MISSING) {
            return cached;
        }
        int type = classify(world.getBlockAt(x, y, z));
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(key, (byte) type);
        return type;
    }

    /** Below the world: treated as a hazard so nobody paths into the void. */
    private static final int HAZARD_VOID = HAZARD;

    private static int classify(Block block) {
        Material material = block.getType();
        if (material.isAir()) {
            return AIR;
        }
        if (HAZARDS.contains(material)) {
            return HAZARD;
        }
        boolean passable = block.isPassable();
        if (WATER_PLANTS.contains(material)) {
            return passable ? WATER : SOLID;
        }
        if (passable) {
            return block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged() ? WATER : AIR;
        }
        BoundingBox top = null;
        for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
            if (top == null || box.getMaxY() > top.getMaxY()) {
                top = box;
            }
        }
        if (top == null) {
            return AIR;
        }
        // Carpets, snow layers and bottom slabs are low enough to step onto: treat the cell as free space.
        return top.getMaxY() <= STEP_HEIGHT && top.getMinY() <= 0.01 ? AIR : SOLID;
    }
}
