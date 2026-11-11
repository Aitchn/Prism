package com.aitchn.prism.core.block;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An immutable snapshot of where the custom blocks of one chunk are and what each must look like to a viewer. It is the
 * only thing the network threads read: the block index replaces the whole snapshot when it changes, so a reader never
 * sees a half-updated chunk and never touches world state.
 *
 * <p>Positions are chunk-local x and z with any y, as {@link BlockPositions#pack}. The arrays are sorted by that key,
 * which orders them by y first, so the entries of one section are contiguous.
 */
public final class ChunkLooks {

    /** The snapshot of a chunk without custom blocks. */
    public static final ChunkLooks EMPTY = new ChunkLooks(new long[0], new int[0], new int[0], new int[0], new int[0]);

    /** Told about a position whose state is neither the carrier nor a legacy carrier: something else replaced it. */
    @FunctionalInterface
    public interface ForeignSink {
        void foreign(int localX, int y, int localZ);
    }

    private final long[] packed;
    private final int[] carrier;
    private final int[] look;
    private final int[] display;
    private final int[] bedrock;

    private ChunkLooks(long[] packed, int[] carrier, int[] look, int[] display, int[] bedrock) {
        this.packed = packed;
        this.carrier = carrier;
        this.look = look;
        this.display = display;
        this.bedrock = bedrock;
    }

    /** Builds a snapshot from {@code packed position -> look}. The map is copied. */
    public static @NotNull ChunkLooks of(@NotNull Map<Long, BlockLook> looks) {
        if (looks.isEmpty()) {
            return EMPTY;
        }
        TreeMap<Long, BlockLook> sorted = new TreeMap<>(looks);
        long[] packed = new long[sorted.size()];
        int[] carrier = new int[packed.length];
        int[] look = new int[packed.length];
        int[] display = new int[packed.length];
        int[] bedrock = new int[packed.length];
        int i = 0;
        for (Map.Entry<Long, BlockLook> entry : sorted.entrySet()) {
            packed[i] = entry.getKey();
            carrier[i] = entry.getValue().carrier();
            look[i] = entry.getValue().look();
            display[i] = entry.getValue().display();
            bedrock[i] = entry.getValue().bedrock();
            i++;
        }
        return new ChunkLooks(packed, carrier, look, display, bedrock);
    }

    public int size() {
        return packed.length;
    }

    public boolean isEmpty() {
        return packed.length == 0;
    }

    /** The look at chunk-local {@code x}, {@code z} and block {@code y}, or null if there is no custom block. */
    public @Nullable BlockLook at(int x, int y, int z) {
        int i = Arrays.binarySearch(packed, BlockPositions.pack(x, y, z));
        return i < 0 ? null : new BlockLook(carrier[i], look[i], display[i], bedrock[i]);
    }

    /** Whether there is a custom block at chunk-local {@code x}, {@code z} and block {@code y}. */
    public boolean has(int x, int y, int z) {
        return Arrays.binarySearch(packed, BlockPositions.pack(x, y, z)) >= 0;
    }

    /** Told about a position drawn by the display layer: chunk-local x and z, block y and the display look id. */
    @FunctionalInterface
    public interface DisplaySink {
        void display(int localX, int y, int localZ, int displayId);
    }

    /** Visits every position of this chunk that is drawn by the display layer. */
    public void forEachDisplay(@NotNull DisplaySink sink) {
        for (int i = 0; i < packed.length; i++) {
            if (display[i] != BlockLook.NO_DISPLAY) {
                sink.display((int) ((packed[i] >> 4) & 15), y(packed[i]), (int) (packed[i] & 15), display[i]);
            }
        }
    }

    /** How many positions of this chunk are drawn by the display layer. */
    public int displayCount() {
        int count = 0;
        for (int d : display) {
            count += d != BlockLook.NO_DISPLAY ? 1 : 0;
        }
        return count;
    }

    /** Whether any position of this chunk is drawn by the display layer. */
    public boolean hasDisplays() {
        for (int d : display) {
            if (d != BlockLook.NO_DISPLAY) {
                return true;
            }
        }
        return false;
    }

    /** Whether any position of this chunk has a synthetic state for Geyser viewers. */
    public boolean hasBedrock() {
        for (int b : bedrock) {
            if (b != BlockLook.NO_BEDROCK) {
                return true;
            }
        }
        return false;
    }

    /** Whether any custom block is in section {@code sectionY} (block y divided by 16, rounded down). */
    public boolean hasSection(int sectionY) {
        int i = lowerBound(sectionY);
        return i < packed.length && Math.floorDiv(y(packed[i]), 16) == sectionY;
    }

    /**
     * Rewrites the custom block positions of one section: a position holding the carrier (or a state {@code legacy}
     * accepts, such as the note block carrier of older versions) gets its look. A position already holding its look is
     * left alone. A position holding anything else was replaced by something outside Prism: it is left as it is and
     * reported to {@code foreign}. Only the positions of the snapshot are visited, so a section without custom blocks
     * costs one binary search.
     *
     * @return the number of positions changed
     */
    public int rewriteSection(int sectionY, @NotNull SectionStates states, @NotNull IntPredicate legacy,
            @NotNull ForeignSink foreign) {
        return rewriteSection(sectionY, states, legacy, foreign, false);
    }

    /**
     * As {@link #rewriteSection(int, SectionStates, IntPredicate, ForeignSink)} for a viewer that may be a Geyser
     * connection: with {@code bedrock} a position that has a synthetic state is rewritten to it instead of the Java
     * look, and a position already holding the Java look (sent by a correction, which knows only Java looks) is moved
     * to the synthetic state too.
     */
    public int rewriteSection(int sectionY, @NotNull SectionStates states, @NotNull IntPredicate legacy,
            @NotNull ForeignSink foreign, boolean bedrock) {
        int changed = 0;
        for (int i = lowerBound(sectionY); i < packed.length; i++) {
            int y = y(packed[i]);
            if (Math.floorDiv(y, 16) != sectionY) {
                break;
            }
            int x = (int) ((packed[i] >> 4) & 15);
            int z = (int) (packed[i] & 15);
            int current = states.get(x, y & 15, z);
            int target = shown(i, bedrock);
            if (current == target) {
                continue;
            }
            if (current == carrier[i] || legacy.test(current) || (target != look[i] && current == look[i])) {
                states.set(x, y & 15, z, target);
                changed++;
            } else {
                foreign.foreign(x, y, z);
            }
        }
        return changed;
    }

    /**
     * The state to send for a single block change at chunk-local {@code x}, {@code z}, {@code y} carrying
     * {@code state}: the look if the position is a custom block holding its carrier or a legacy carrier, otherwise
     * {@code state} itself (reporting a foreign state to {@code foreign}).
     */
    public int rewrite(int x, int y, int z, int state, @NotNull IntPredicate legacy, @NotNull ForeignSink foreign) {
        return rewrite(x, y, z, state, legacy, foreign, false);
    }

    /** As {@link #rewrite(int, int, int, int, IntPredicate, ForeignSink)}, for a viewer that may be a Geyser connection. */
    public int rewrite(int x, int y, int z, int state, @NotNull IntPredicate legacy, @NotNull ForeignSink foreign,
            boolean bedrock) {
        int i = Arrays.binarySearch(packed, BlockPositions.pack(x, y, z));
        if (i < 0) {
            return state;
        }
        int target = shown(i, bedrock);
        if (state == target) {
            return state;
        }
        if (state == carrier[i] || legacy.test(state) || (target != look[i] && state == look[i])) {
            return target;
        }
        foreign.foreign(x, y, z);
        return state;
    }

    private int shown(int i, boolean bedrockViewer) {
        return bedrockViewer && bedrock[i] != BlockLook.NO_BEDROCK ? bedrock[i] : look[i];
    }

    /** The index of the first entry in section {@code sectionY} or later. */
    private int lowerBound(int sectionY) {
        long key = (long) (sectionY * 16) << 8;
        int i = Arrays.binarySearch(packed, key);
        return i >= 0 ? i : -i - 1;
    }

    private static int y(long packed) {
        return (int) (packed >> 8);
    }
}
