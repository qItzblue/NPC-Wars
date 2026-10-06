package com.npcwars.stick;

import java.util.ArrayList;
import java.util.List;

/** An axis-aligned box of whole blocks between two corners. Pure maths so it can be tested without a server. */
public record Area(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** The box that contains both corner blocks. */
    public static Area of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Area(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    /** @return whether a point (block coordinates may be fractional) lies inside the box, edges included */
    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX + 1.0 && y >= minY && y < maxY + 2.0 && z >= minZ && z < maxZ + 1.0;
    }

    /**
     * Spots on the ground plane to stand on: a grid with the given spacing, centred in the box, at most {@code limit}
     * of them. Each entry is {@code {x, z}} of a block centre.
     */
    public List<double[]> gridSpots(int spacing, int limit) {
        int step = Math.max(1, spacing);
        List<double[]> spots = new ArrayList<>();
        int countX = Math.max(1, (sizeX() - 1) / step + 1);
        int countZ = Math.max(1, (sizeZ() - 1) / step + 1);
        double offsetX = (sizeX() - 1 - (countX - 1) * step) / 2.0;
        double offsetZ = (sizeZ() - 1 - (countZ - 1) * step) / 2.0;
        for (int ix = 0; ix < countX; ix++) {
            for (int iz = 0; iz < countZ; iz++) {
                if (spots.size() >= limit) {
                    return spots;
                }
                spots.add(new double[] {minX + offsetX + ix * step + 0.5, minZ + offsetZ + iz * step + 0.5});
            }
        }
        return spots;
    }
}
