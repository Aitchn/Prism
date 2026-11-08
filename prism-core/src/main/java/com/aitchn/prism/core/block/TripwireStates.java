package com.aitchn.prism.core.block;

import org.jetbrains.annotations.NotNull;

/**
 * The tripwire states Prism draws passable blocks with: the 64 with {@code disarmed=true}. Vanilla string only takes
 * that value for the moment shears cut it, so these states never stay in the world on their own. A tripwire has no
 * collision and does not hide its neighbours, so a block drawn by one can be walked through and seen around.
 *
 * <p>State ids share one number space with note block states so a look maps to a single int: tripwire ids start at
 * {@link #OFFSET}.
 */
public final class TripwireStates {

    public static final int OFFSET = 1000;
    public static final int COUNT = 64;

    /** One allocatable state. */
    public record State(boolean attached, boolean east, boolean north, boolean powered, boolean south, boolean west) {
        /** The variant key used in a blockstates file and a Geyser mapping (properties in alphabetical order). */
        public String variant() {
            return TripwireStates.variant(attached, true, east, north, powered, south, west);
        }
    }

    private TripwireStates() {}

    /** Whether {@code id} names a tripwire state rather than a note block state. */
    public static boolean isTripwire(int id) {
        return id >= OFFSET && id < OFFSET + COUNT;
    }

    public static @NotNull State state(int id) {
        if (!isTripwire(id)) {
            throw new IllegalArgumentException("tripwire state id out of range: " + id);
        }
        // attached and powered vary first: the first four states have no side set, so a vanilla shape update (string
        // connecting to its neighbours) cannot change them even if it ran.
        int bits = id - OFFSET;
        return new State((bits & 1) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 2) != 0, (bits & 16) != 0,
                (bits & 32) != 0);
    }

    public static @NotNull String variant(boolean attached, boolean disarmed, boolean east, boolean north,
            boolean powered, boolean south, boolean west) {
        return "attached=" + attached + ",disarmed=" + disarmed + ",east=" + east + ",north=" + north + ",powered="
                + powered + ",south=" + south + ",west=" + west;
    }

    /** The vanilla model and y rotation of a string with these connections, as the game's own blockstates file. */
    public static @NotNull VanillaModel vanilla(boolean attached, boolean east, boolean north, boolean south,
            boolean west) {
        String prefix = "minecraft:block/tripwire_" + (attached ? "attached_" : "");
        int count = (north ? 1 : 0) + (east ? 1 : 0) + (south ? 1 : 0) + (west ? 1 : 0);
        return switch (count) {
            case 0 -> new VanillaModel(prefix + "ns", 0);
            case 1 -> new VanillaModel(prefix + "n", north ? 0 : east ? 90 : south ? 180 : 270);
            case 2 -> {
                if (north && south) {
                    yield new VanillaModel(prefix + "ns", 0);
                }
                if (east && west) {
                    yield new VanillaModel(prefix + "ns", 90);
                }
                // Two sides at a corner: "ne" turned so its north arm lands on the first side clockwise.
                yield new VanillaModel(prefix + "ne", north && east ? 0 : east && south ? 90 : south && west ? 180 : 270);
            }
            case 3 -> new VanillaModel(prefix + "nse", !west ? 0 : !north ? 90 : !east ? 180 : 270);
            default -> new VanillaModel(prefix + "nsew", 0);
        };
    }

    /** A vanilla string model and its y rotation. */
    public record VanillaModel(@NotNull String model, int y) {}
}
