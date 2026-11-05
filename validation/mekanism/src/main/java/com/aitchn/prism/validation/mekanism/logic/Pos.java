package com.aitchn.prism.validation.mekanism.logic;

import java.util.List;

/** A block position in a named world. Bukkit-free so the machine logic runs in plain unit tests. */
public record Pos(String world, int x, int y, int z) {

    public Pos offset(int dx, int dy, int dz) {
        return new Pos(world, x + dx, y + dy, z + dz);
    }

    public List<Pos> neighbors() {
        return List.of(offset(1, 0, 0), offset(-1, 0, 0), offset(0, 1, 0), offset(0, -1, 0), offset(0, 0, 1),
                offset(0, 0, -1));
    }

    @Override
    public String toString() {
        return world + ":" + x + "," + y + "," + z;
    }
}
