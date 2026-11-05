package com.aitchn.validation.botania.logic;

import java.util.Objects;

/** A block position in a named world. Bukkit-free so all rules can be unit tested without a server. */
public record Pos(String world, int x, int y, int z) {

    public Pos {
        Objects.requireNonNull(world, "world");
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    public Pos offset(int dx, int dy, int dz) {
        return new Pos(world, x + dx, y + dy, z + dz);
    }

    public double distanceSquared(Pos other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Text form, {@code x,y,z}; the world is implied by where it is stored. */
    public String coords() {
        return x + "," + y + "," + z;
    }

    public static Pos parseCoords(String world, String text) {
        String[] parts = text.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("expected x,y,z but got '" + text + "'");
        }
        return new Pos(world, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    /** DataStore key prefix of every block entity in this position's chunk. */
    public String chunkPrefix() {
        return chunkPrefix(world, chunkX(), chunkZ());
    }

    public static String chunkPrefix(String world, int chunkX, int chunkZ) {
        return "be/" + world + "/" + chunkX + "," + chunkZ + "/";
    }

    /** Full DataStore key of the block entity at this position. */
    public String storeKey() {
        return chunkPrefix() + coords();
    }
}
