package com.npcwars.path;

/**
 * Read-only view of the blocks around an NPC, reduced to what path planning needs. Keeping this an interface keeps the
 * A* implementation free of Bukkit and unit-testable.
 */
public interface Terrain {

    /** Free space the body can occupy (also non-colliding blocks such as grass, signs or low slabs). */
    int AIR = 0;
    /** A block that blocks movement and can be stood on. */
    int SOLID = 1;
    /** Swimmable water. */
    int WATER = 2;
    /** Lava, fire, cactus, ... never entered and never stood on. */
    int HAZARD = 3;
    /** Not loaded (or outside the world); never entered, and never loaded by the planner. */
    int UNLOADED = 4;

    /** @return one of the constants above for the block at the given position */
    int type(int x, int y, int z);

    /** @return {@code true} if a body can occupy the cell (air or water) */
    static boolean isPassable(int type) {
        return type == AIR || type == WATER;
    }
}
