package com.aitchn.prism.validation.ae2.grid;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** A block position that does not depend on Bukkit, so grid logic can be tested without a server. */
public record GridPos(@NotNull String world, int x, int y, int z) implements Comparable<GridPos> {

    private static final Comparator<GridPos> ORDER =
            Comparator.comparing(GridPos::world).thenComparingInt(GridPos::x).thenComparingInt(GridPos::y)
                    .thenComparingInt(GridPos::z);

    public GridPos {
        Objects.requireNonNull(world, "world");
        if (world.indexOf('|') >= 0) {
            throw new IllegalArgumentException("world name must not contain '|': " + world);
        }
    }

    /** The six face neighbours. */
    public @NotNull List<GridPos> neighbors() {
        return List.of(new GridPos(world, x + 1, y, z), new GridPos(world, x - 1, y, z),
                new GridPos(world, x, y + 1, z), new GridPos(world, x, y - 1, z),
                new GridPos(world, x, y, z + 1), new GridPos(world, x, y, z - 1));
    }

    public @NotNull String encode() {
        return world + "|" + x + "|" + y + "|" + z;
    }

    public static @NotNull GridPos decode(@NotNull String text) {
        String[] parts = text.split("\\|");
        if (parts.length != 4) {
            throw new IllegalArgumentException("not a grid position: " + text);
        }
        return new GridPos(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]));
    }

    @Override
    public int compareTo(@NotNull GridPos other) {
        return ORDER.compare(this, other);
    }
}
