package com.aitchn.prism.core.block;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Lays out the 4096 block states of one chunk section the way a 1.18+ paletted container is sent, for any state id and
 * any palette width. The rewrite for Geyser viewers needs this because a synthetic state id is larger than any state
 * of the server version: the server's own global palette may be too narrow for it (and a packet library's global
 * palette has a fixed width), so a section that holds one is written again with a palette that fits.
 *
 * <p>The palette kinds follow the protocol: no entries of its own for one state ({@link Kind#SINGLETON}, 0 bits), a
 * list of up to 16 states ({@link Kind#LIST}, 4 bits), a map of up to 256 ({@link Kind#MAP}, 5 to 8 bits), and the state
 * ids themselves ({@link Kind#GLOBAL}, more than 8 bits, as wide as the reader's registry needs). Data is packed the way
 * vanilla does: {@code 64 / bits} values per long, none spanning two longs, in y, z, x order.
 */
public final class PalettedSection {

    /** Blocks in a section. */
    public static final int SIZE = 4096;

    private static final int MAX_LIST = 16;
    private static final int MAX_MAP = 256;

    public enum Kind { SINGLETON, LIST, MAP, GLOBAL }

    /**
     * @param kind    how the states are numbered
     * @param bits    bits per block as written ({@code 0} for a singleton)
     * @param palette the states by palette index (the one state of a singleton); empty for {@link Kind#GLOBAL}
     * @param data    the packed indices (or states, for global); empty for a singleton
     */
    public record Encoded(@NotNull Kind kind, int bits, int @NotNull [] palette, long @NotNull [] data) {}

    private PalettedSection() {}

    /** The index of a block in a section, y outermost then z then x. */
    public static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    /**
     * Chooses the palette for {@code states} (indexed by {@link #index}) and packs them.
     *
     * @param globalBits bits per block when the section needs the global palette: at least what every state id of the
     *                   reader's registry needs (Geyser derives it from its registry size), and more than 8
     * @throws IllegalArgumentException if a state does not fit in {@code globalBits} where the global palette is needed
     */
    public static @NotNull Encoded encode(int @NotNull [] states, int globalBits) {
        if (states.length != SIZE) {
            throw new IllegalArgumentException("a section has " + SIZE + " blocks, got " + states.length);
        }
        Map<Integer, Integer> palette = new LinkedHashMap<>();
        boolean overflow = false;
        for (int state : states) {
            if (!palette.containsKey(state)) {
                if (palette.size() == MAX_MAP) {
                    overflow = true;
                    break;
                }
                palette.put(state, palette.size());
            }
        }
        if (overflow) {
            int bits = Math.max(globalBits, 9);
            int max = 0;
            for (int state : states) {
                max = Math.max(max, state);
            }
            if (max >= (1L << bits)) {
                throw new IllegalArgumentException("state " + max + " does not fit in " + bits + " bits");
            }
            return new Encoded(Kind.GLOBAL, bits, new int[0], pack(states, bits));
        }
        int[] entries = palette.keySet().stream().mapToInt(Integer::intValue).toArray();
        if (entries.length == 1) {
            return new Encoded(Kind.SINGLETON, 0, entries, new long[0]);
        }
        int[] indices = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            indices[i] = palette.get(states[i]);
        }
        if (entries.length <= MAX_LIST) {
            return new Encoded(Kind.LIST, 4, entries, pack(indices, 4));
        }
        int bits = Math.max(5, bitsFor(entries.length));
        return new Encoded(Kind.MAP, bits, entries, pack(indices, bits));
    }

    /** The states of a section laid out by {@link #encode}, indexed by {@link #index}. */
    public static int @NotNull [] decode(@NotNull Encoded encoded) {
        int[] states = new int[SIZE];
        if (encoded.kind() == Kind.SINGLETON) {
            Arrays.fill(states, encoded.palette()[0]);
            return states;
        }
        int[] values = unpack(encoded.data(), encoded.bits(), SIZE);
        for (int i = 0; i < SIZE; i++) {
            states[i] = encoded.kind() == Kind.GLOBAL ? values[i] : encoded.palette()[values[i]];
        }
        return states;
    }

    /** Packs {@code values} with {@code bits} bits each, {@code 64 / bits} to a long, none crossing a long. */
    public static long @NotNull [] pack(int @NotNull [] values, int bits) {
        if (bits < 1 || bits > 32) {
            throw new IllegalArgumentException("bits must be 1 to 32, got " + bits);
        }
        int perLong = 64 / bits;
        long[] data = new long[(values.length + perLong - 1) / perLong];
        long mask = (1L << bits) - 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] < 0 || values[i] > mask) {
                throw new IllegalArgumentException("value " + values[i] + " does not fit in " + bits + " bits");
            }
            data[i / perLong] |= ((long) values[i] & mask) << ((i % perLong) * bits);
        }
        return data;
    }

    /** The inverse of {@link #pack}. */
    public static int @NotNull [] unpack(long @NotNull [] data, int bits, int size) {
        int perLong = 64 / bits;
        if (data.length != (size + perLong - 1) / perLong) {
            throw new IllegalArgumentException("expected " + (size + perLong - 1) / perLong + " longs, got " + data.length);
        }
        long mask = (1L << bits) - 1;
        int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = (int) ((data[i / perLong] >>> ((i % perLong) * bits)) & mask);
        }
        return values;
    }

    /** Bits needed to number {@code count} entries: the ceiling of log2, at least 1. */
    public static int bitsFor(int count) {
        return count <= 2 ? 1 : 32 - Integer.numberOfLeadingZeros(count - 1);
    }
}
