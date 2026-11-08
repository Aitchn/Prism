package com.aitchn.prism.core.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import org.jetbrains.annotations.NotNull;

/**
 * Where the veins of one ore spec go in one chunk. Pure and deterministic: the same world seed, chunk and spec always
 * give the same blocks, so a regenerated chunk matches. A vein is a random walk of {@code size} steps from a random
 * start; it never leaves its chunk, so generation does not touch neighbouring chunks.
 */
public final class OreVeins {

    private static final int[][] STEPS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private OreVeins() {}

    /** One spec: veins per chunk, chance one in {@code rarity}, blocks per vein and the height range (inclusive). */
    public record Spec(int veinsPerChunk, int rarity, int size, int minY, int maxY) {}

    /** A block position inside the chunk: {@code x} and {@code z} from 0 to 15, {@code y} absolute. */
    public record Position(int x, int y, int z) {}

    /**
     * Candidate positions for {@code spec} in chunk ({@code chunkX}, {@code chunkZ}). {@code salt} tells specs apart
     * (for example the hash of the block key and the spec index). Heights are clamped to the world's
     * {@code [worldMinY, worldMaxY)}; an empty list means the range is outside the world or the rarity roll failed.
     * The caller still checks that each position holds a replaceable block.
     */
    public static @NotNull List<Position> positions(@NotNull Spec spec, long seed, long salt, int chunkX, int chunkZ,
            int worldMinY, int worldMaxY) {
        int low = Math.max(spec.minY(), worldMinY);
        int high = Math.min(spec.maxY(), worldMaxY - 1);
        if (low > high) {
            return List.of();
        }
        SplittableRandom random = new SplittableRandom(mix(seed, salt, chunkX, chunkZ));
        if (spec.rarity() > 1 && random.nextInt(spec.rarity()) != 0) {
            return List.of();
        }
        Set<Position> out = new LinkedHashSet<>();
        for (int vein = 0; vein < spec.veinsPerChunk(); vein++) {
            int x = random.nextInt(16);
            int y = low + random.nextInt(high - low + 1);
            int z = random.nextInt(16);
            for (int step = 0; step < spec.size(); step++) {
                out.add(new Position(x, y, z));
                int[] move = STEPS[random.nextInt(STEPS.length)];
                x = bounce(x + move[0], 0, 15);
                y = bounce(y + move[1], low, high);
                z = bounce(z + move[2], 0, 15);
            }
        }
        return new ArrayList<>(out);
    }

    private static int bounce(int value, int min, int max) {
        if (value < min) {
            return Math.min(max, min + 1);
        }
        if (value > max) {
            return Math.max(min, max - 1);
        }
        return value;
    }

    private static long mix(long seed, long salt, int chunkX, int chunkZ) {
        long h = seed ^ (salt * 0x9E3779B97F4A7C15L);
        h ^= chunkX * 341873128712L + chunkZ * 132897987541L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }
}
