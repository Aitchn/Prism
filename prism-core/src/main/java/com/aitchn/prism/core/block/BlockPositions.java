package com.aitchn.prism.core.block;

/** Encodes a block position inside a chunk as a persistent-data key path and as a long. */
public final class BlockPositions {

    private BlockPositions() {}

    /** {@code b/<x>/<y>/<z>} with chunk-local x and z (0..15); valid as a {@code NamespacedKey} value. */
    public static String key(int localX, int y, int localZ) {
        check(localX, localZ);
        return "b/" + localX + "/" + y + "/" + localZ;
    }

    /** The inverse of {@link #key}: {localX, y, localZ}, or null if {@code key} is not one. */
    public static int[] parse(String key) {
        if (!key.startsWith("b/")) {
            return null;
        }
        String[] parts = key.substring(2).split("/");
        if (parts.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            if (x < 0 || x > 15 || z < 0 || z > 15) {
                return null;
            }
            return new int[] {x, y, z};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Packs chunk-local x, z and any int y into one long, unique per chunk. */
    public static long pack(int localX, int y, int localZ) {
        check(localX, localZ);
        return ((long) y << 8) | (localX << 4) | localZ;
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static void check(int localX, int localZ) {
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            throw new IllegalArgumentException("chunk-local coordinates out of range: " + localX + ", " + localZ);
        }
    }
}
