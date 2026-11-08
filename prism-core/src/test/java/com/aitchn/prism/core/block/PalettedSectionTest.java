package com.aitchn.prism.core.block;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.core.bedrock.SyntheticIds;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Round trips sections with synthetic state ids through the wire format of a 1.18+ paletted container, read by a
 * reader written the way a client reads it (the bits per entry byte decides the palette kind, the longs hold
 * {@code 64 / bits} values each), so a layout bug in the encoder cannot hide behind a matching bug in a decoder.
 */
class PalettedSectionTest {

    private static final int VANILLA = 29_000;
    private static final int BASE = SyntheticIds.defaultBase(VANILLA);

    // ---- the reader: what a client does with the bytes -------------------------------------------------------

    private static void varInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static byte[] write(PalettedSection.Encoded encoded, boolean lengthPrefix) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(encoded.bits());
        if (encoded.kind() == PalettedSection.Kind.SINGLETON) {
            varInt(out, encoded.palette()[0]);
            if (lengthPrefix) {
                varInt(out, 0);
            }
            return out.toByteArray();
        }
        if (encoded.kind() != PalettedSection.Kind.GLOBAL) {
            varInt(out, encoded.palette().length);
            for (int state : encoded.palette()) {
                varInt(out, state);
            }
        }
        if (lengthPrefix) {
            varInt(out, encoded.data().length);
        }
        ByteBuffer longs = ByteBuffer.allocate(encoded.data().length * 8);
        for (long value : encoded.data()) {
            longs.putLong(value);
        }
        out.writeBytes(longs.array());
        return out.toByteArray();
    }

    private static int readVarInt(ByteBuffer in) {
        int value = 0;
        for (int shift = 0;; shift += 7) {
            int b = in.get() & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
    }

    /** Reads one container: bits, then a palette chosen by the bits, then the packed storage. */
    private static int[] read(byte[] bytes, boolean lengthPrefix) {
        ByteBuffer in = ByteBuffer.wrap(bytes);
        int bits = in.get() & 0xFF;
        int[] states = new int[PalettedSection.SIZE];
        if (bits == 0) {
            int only = readVarInt(in);
            if (lengthPrefix) {
                assertEquals(0, readVarInt(in));
            }
            java.util.Arrays.fill(states, only);
            assertEquals(0, in.remaining());
            return states;
        }
        int[] palette = null;
        if (bits <= 8) {
            palette = new int[readVarInt(in)];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = readVarInt(in);
            }
        }
        int perLong = 64 / bits;
        int longs = (PalettedSection.SIZE + perLong - 1) / perLong;
        if (lengthPrefix) {
            assertEquals(longs, readVarInt(in));
        }
        long[] data = new long[longs];
        for (int i = 0; i < longs; i++) {
            data[i] = in.getLong();
        }
        assertEquals(0, in.remaining(), "no trailing bytes");
        for (int i = 0; i < PalettedSection.SIZE; i++) {
            long cell = data[i / perLong];
            int value = (int) ((cell >>> ((i % perLong) * bits)) & ((1L << bits) - 1));
            states[i] = palette == null ? value : palette[value];
        }
        return states;
    }

    private static void assertRoundTrip(int[] states, int globalBits, PalettedSection.Kind kind, int bits) {
        PalettedSection.Encoded encoded = PalettedSection.encode(states, globalBits);
        assertEquals(kind, encoded.kind());
        assertEquals(bits, encoded.bits());
        for (boolean lengthPrefix : new boolean[] {true, false}) {
            assertArrayEquals(states, read(write(encoded, lengthPrefix), lengthPrefix));
        }
        assertArrayEquals(states, PalettedSection.decode(encoded));
    }

    // ---- tests -----------------------------------------------------------------------------------------------

    @Test
    void oneStateIsASingleton() {
        int[] states = new int[4096];
        java.util.Arrays.fill(states, BASE + 5);
        assertRoundTrip(states, 16, PalettedSection.Kind.SINGLETON, 0);
    }

    @Test
    void fewStatesUseAFourBitList() {
        int[] states = new int[4096];
        states[PalettedSection.index(1, 2, 3)] = BASE + 1;
        states[PalettedSection.index(15, 15, 15)] = BASE + 2;
        states[PalettedSection.index(0, 0, 0)] = 1;
        assertRoundTrip(states, 16, PalettedSection.Kind.LIST, 4);
        assertEquals(4, PalettedSection.encode(states, 16).palette().length);
    }

    @Test
    void sixteenAndSeventeenStatesChangeTheKindAtTheRightPlace() {
        int[] states = distinct(16);
        assertRoundTrip(states, 16, PalettedSection.Kind.LIST, 4);
        assertRoundTrip(distinct(17), 16, PalettedSection.Kind.MAP, 5);
        assertRoundTrip(distinct(64), 16, PalettedSection.Kind.MAP, 6);
        assertRoundTrip(distinct(65), 16, PalettedSection.Kind.MAP, 7);
        assertRoundTrip(distinct(256), 16, PalettedSection.Kind.MAP, 8);
    }

    @Test
    void moreThan256StatesNeedTheGlobalPaletteAtTheRegistryWidth() {
        int count = 1000;
        int[] states = distinct(count);
        SyntheticIds.Plan plan = SyntheticIds.plan(lookIds(10_000), BASE);
        assertEquals(16, plan.globalBits());
        assertRoundTrip(states, plan.globalBits(), PalettedSection.Kind.GLOBAL, 16);
        PalettedSection.Encoded encoded = PalettedSection.encode(states, plan.globalBits());
        assertEquals(0, encoded.palette().length);
        assertEquals((4096 + 3) / 4, encoded.data().length, "four 16 bit values per long");
    }

    @Test
    void widthsPastSixteenBitsKeepTheirLayout() {
        // 40,000 looks above the vanilla range need 17 bits: three values per long, a different longs count
        SyntheticIds.Plan plan = SyntheticIds.plan(lookIds(40_000), BASE);
        assertEquals(17, plan.globalBits());
        int[] states = new int[4096];
        Random random = new Random(11);
        for (int i = 0; i < 4096; i++) {
            states[i] = random.nextInt(3) == 0 ? random.nextInt(VANILLA) : BASE + random.nextInt(40_000);
        }
        PalettedSection.Encoded encoded = PalettedSection.encode(states, plan.globalBits());
        assertEquals(PalettedSection.Kind.GLOBAL, encoded.kind());
        assertEquals(17, encoded.bits());
        assertEquals((4096 + 2) / 3, encoded.data().length);
        assertArrayEquals(states, read(write(encoded, false), false));
    }

    @Test
    void randomSectionsOfEveryKindRoundTrip() {
        Random random = new Random(2026);
        int globalBits = SyntheticIds.plan(lookIds(10_000), BASE).globalBits();
        for (int round = 0; round < 60; round++) {
            int distinct = 1 + random.nextInt(round % 3 == 0 ? 3 : round % 3 == 1 ? 200 : 3000);
            int[] pool = new int[distinct];
            for (int i = 0; i < distinct; i++) {
                pool[i] = random.nextBoolean() ? random.nextInt(VANILLA) : BASE + random.nextInt(10_000);
            }
            int[] states = new int[4096];
            for (int i = 0; i < 4096; i++) {
                states[i] = pool[random.nextInt(distinct)];
            }
            PalettedSection.Encoded encoded = PalettedSection.encode(states, globalBits);
            assertArrayEquals(states, read(write(encoded, round % 2 == 0), round % 2 == 0), "round " + round);
            assertArrayEquals(states, PalettedSection.decode(encoded));
        }
    }

    @Test
    void theFirstLongHoldsTheFirstValuesInTheLowBits() {
        int[] indices = new int[4096];
        for (int i = 0; i < 16; i++) {
            indices[i] = i;
        }
        long[] packed = PalettedSection.pack(indices, 4);
        assertEquals(0xFEDCBA9876543210L, packed[0]);
        assertEquals(4096 / 16, packed.length);
        assertArrayEquals(indices, PalettedSection.unpack(packed, 4, 4096));
    }

    @Test
    void aStateThatDoesNotFitTheGlobalWidthIsRefused() {
        int[] states = distinct(300);
        states[7] = 70_000;
        assertThrows(IllegalArgumentException.class, () -> PalettedSection.encode(states, 16));
        assertTrue(PalettedSection.encode(states, 17).bits() >= 17);
        assertThrows(IllegalArgumentException.class, () -> PalettedSection.encode(new int[10], 16));
    }

    private static int[] distinct(int count) {
        int[] states = new int[4096];
        for (int i = 0; i < 4096; i++) {
            states[i] = i < count ? (i % 2 == 0 ? i : BASE + i) : 0;
        }
        return states;
    }

    private static List<String> lookIds(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add("synth:b" + i);
        }
        return ids;
    }
}
