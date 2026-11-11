package com.aitchn.prism.core.look;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/**
 * Nearest-first selection of display entities under a per-viewer budget ({@code blocks.display.max-per-player},
 * ADR-0025). Two levels, so that a viewer moving only costs work proportional to the chunks it has, never to the
 * blocks in them:
 *
 * <ol>
 *   <li>{@link #allowances}: the chunks the viewer has, nearest first (by the distance from the viewer to the chunk's
 *       centre, then by chunk key), each take all the entities they need while that fits in the budget. The first
 *       chunk that does not fit takes what is left; every chunk farther away gets nothing.
 *   <li>{@link #nearest}: inside that boundary chunk, blocks are taken nearest first (by the distance from the viewer
 *       to the block's centre) until the next one does not fit.
 * </ol>
 *
 * <p>So a viewer is never sent more than the budget, the chunks are filled in rings around the viewer, and what is
 * dropped is the farthest. Pure functions; the display layer calls them again when the viewer crosses a chunk border,
 * when a chunk arrives or leaves, and when the number of entities a chunk needs changes.
 */
public final class DisplayBudget {

    /** Allowance of a chunk that is not admitted at all. */
    public static final int NONE = -1;

    private DisplayBudget() {}

    /** Squared horizontal distance from block {@code (x, z)} to the centre of chunk {@code (chunkX, chunkZ)}. */
    public static long chunkDistanceSq(int x, int z, int chunkX, int chunkZ) {
        long dx = (long) (chunkX << 4) + 8 - x;
        long dz = (long) (chunkZ << 4) + 8 - z;
        return dx * dx + dz * dz;
    }

    /**
     * Squared distance, in eighths of a block squared, from block position {@code (x, y, z)} of the viewer to the
     * centre of block {@code (bx, by, bz)}. Integer maths, so ordering is exact and stable.
     */
    public static long blockDistanceSq(int x, int y, int z, int bx, int by, int bz) {
        long dx = 2L * (bx - x);
        long dy = 2L * (by - y);
        long dz = 2L * (bz - z);
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * How many entities each chunk may show.
     *
     * @param budget     the most entities the viewer may have
     * @param keys       chunk keys (only used to order chunks at the same distance)
     * @param distanceSq distance of each chunk, see {@link #chunkDistanceSq}
     * @param needs      how many entities each chunk needs to show all of its blocks (an upper bound is fine)
     * @return per chunk, in input order: its allowance ({@code needs[i]} for a chunk that fits whole, less for the
     *         boundary chunk), or {@link #NONE} for a chunk that gets nothing. The allowances sum to at most
     *         {@code budget}
     */
    public static int @NotNull [] allowances(int budget, long @NotNull [] keys, long @NotNull [] distanceSq,
            int @NotNull [] needs) {
        int n = keys.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> distanceSq[a] != distanceSq[b] ? Long.compare(distanceSq[a], distanceSq[b])
                : Long.compare(keys[a], keys[b]));
        int[] allowance = new int[n];
        Arrays.fill(allowance, NONE);
        long left = Math.max(0, budget);
        for (int i : order) {
            int need = Math.max(0, needs[i]);
            if (need <= left) {
                allowance[i] = need;
                left -= need;
                continue;
            }
            if (left > 0) {
                allowance[i] = (int) left;
            }
            break;
        }
        return allowance;
    }

    /** The key of chunk {@code (chunkX, chunkZ)}, as Bukkit's {@code Chunk.getChunkKey} computes it. */
    public static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    /**
     * The whole selection at once, once every chunk's need is known: what the display layer converges to for a viewer
     * standing at block {@code (x, y, z)}.
     *
     * @param chunkX    chunk x of each chunk the viewer has
     * @param chunkZ    chunk z of each chunk
     * @param blockKeys per chunk, the {@link DisplayHeld#key} of each display block
     * @param weights   per chunk and block, how many entities the block needs (0 for an enclosed block)
     * @return per chunk and block, whether the block's entities are sent
     */
    public static boolean @NotNull [] @NotNull [] select(int budget, int x, int y, int z, int @NotNull [] chunkX,
            int @NotNull [] chunkZ, int @NotNull [] @NotNull [] blockKeys, int @NotNull [] @NotNull [] weights) {
        int n = chunkX.length;
        long[] keys = new long[n];
        long[] distances = new long[n];
        int[] needs = new int[n];
        for (int c = 0; c < n; c++) {
            keys[c] = chunkKey(chunkX[c], chunkZ[c]);
            distances[c] = chunkDistanceSq(x, z, chunkX[c], chunkZ[c]);
            for (int w : weights[c]) {
                needs[c] += w;
            }
        }
        int[] allowance = allowances(budget, keys, distances, needs);
        boolean[][] chosen = new boolean[n][];
        for (int c = 0; c < n; c++) {
            int[] blocks = blockKeys[c];
            if (allowance[c] == NONE) {
                chosen[c] = new boolean[blocks.length];
                continue;
            }
            long[] blockDistances = new long[blocks.length];
            for (int b = 0; b < blocks.length; b++) {
                blockDistances[b] = blockDistanceSq(x, y, z, (chunkX[c] << 4) + DisplayHeld.keyX(blocks[b]),
                        DisplayHeld.keyY(blocks[b]), (chunkZ[c] << 4) + DisplayHeld.keyZ(blocks[b]));
            }
            chosen[c] = nearest(allowance[c], blockDistances, weights[c]);
        }
        return chosen;
    }

    /**
     * Which blocks of one chunk to show within {@code limit} entities: nearest first (ties by index), each taking its
     * weight (the number of entities it needs), until the next one does not fit.
     *
     * @return per block, in input order, whether it is shown
     */
    public static boolean @NotNull [] nearest(int limit, long @NotNull [] distanceSq, int @NotNull [] weights) {
        int n = distanceSq.length;
        boolean[] chosen = new boolean[n];
        long[] packed = new long[n];
        // Sort by distance with the index as tie-break without boxing: distances fit in 40 bits for any real world.
        for (int i = 0; i < n; i++) {
            packed[i] = (Math.min(distanceSq[i], (1L << 40) - 1) << 23) | i;
        }
        Arrays.sort(packed);
        long left = Math.max(0, limit);
        for (long entry : packed) {
            int i = (int) (entry & ((1 << 23) - 1));
            int weight = Math.max(0, weights[i]);
            if (weight > left) {
                break;
            }
            chosen[i] = true;
            left -= weight;
        }
        return chosen;
    }
}
