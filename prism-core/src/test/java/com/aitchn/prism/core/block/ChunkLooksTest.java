package com.aitchn.prism.core.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

class ChunkLooksTest {

    private static final int AIR = 0;
    private static final int STONE = 1;
    private static final int CARRIER = 100;
    private static final int LEGACY = 200;
    private static final IntPredicate IS_LEGACY = state -> state == LEGACY;

    /** A section backed by an array, like a decoded chunk palette. */
    private static final class ArraySection implements SectionStates {
        final int[] states = new int[4096];

        @Override
        public int get(int x, int y, int z) {
            return states[(y << 8) | (z << 4) | x];
        }

        @Override
        public void set(int x, int y, int z, int state) {
            states[(y << 8) | (z << 4) | x] = state;
        }
    }

    private static ChunkLooks looks(Object... xyzLook) {
        Map<Long, BlockLook> map = new HashMap<>();
        for (int i = 0; i < xyzLook.length; i += 4) {
            map.put(BlockPositions.pack((int) xyzLook[i], (int) xyzLook[i + 1], (int) xyzLook[i + 2]),
                    new BlockLook(CARRIER, (int) xyzLook[i + 3]));
        }
        return ChunkLooks.of(map);
    }

    @Test
    void emptySnapshotHasNothing() {
        assertTrue(ChunkLooks.of(Map.of()).isEmpty());
        assertFalse(ChunkLooks.EMPTY.hasSection(0));
        assertNull(ChunkLooks.EMPTY.at(0, 0, 0));
    }

    @Test
    void snapshotDoesNotChangeWhenTheSourceDoes() {
        Map<Long, BlockLook> source = new HashMap<>();
        source.put(BlockPositions.pack(1, 2, 3), new BlockLook(CARRIER, 300));
        ChunkLooks looks = ChunkLooks.of(source);
        source.put(BlockPositions.pack(4, 5, 6), new BlockLook(CARRIER, 301));
        source.remove(BlockPositions.pack(1, 2, 3));
        assertEquals(1, looks.size());
        assertEquals(300, looks.at(1, 2, 3).look());
        assertNull(looks.at(4, 5, 6));
    }

    @Test
    void lookupAndSectionMembership() {
        ChunkLooks looks = looks(1, 5, 2, 300, 15, 70, 15, 301, 0, -64, 0, 302);
        assertEquals(3, looks.size());
        assertEquals(300, looks.at(1, 5, 2).look());
        assertEquals(CARRIER, looks.at(1, 5, 2).carrier());
        assertEquals(301, looks.at(15, 70, 15).look());
        assertEquals(302, looks.at(0, -64, 0).look());
        assertNull(looks.at(1, 6, 2));
        assertTrue(looks.hasSection(0));
        assertTrue(looks.hasSection(4)); // y 70
        assertTrue(looks.hasSection(-4)); // y -64
        assertFalse(looks.hasSection(1));
        assertFalse(looks.hasSection(-3));
    }

    @Test
    void sectionRewriteReplacesOnlyIndexedCarriers() {
        ChunkLooks looks = looks(1, 5, 2, 300, 15, 15, 15, 301, 3, 70, 3, 399);
        ArraySection section = new ArraySection();
        section.set(1, 5, 2, CARRIER);
        section.set(15, 15, 15, LEGACY); // an old note block carrier
        section.set(7, 7, 7, CARRIER); // a real vanilla block of the carrier type: not indexed, untouched
        List<String> foreign = new ArrayList<>();
        int changed = looks.rewriteSection(0, section, IS_LEGACY, (x, y, z) -> foreign.add(x + "," + y + "," + z));
        assertEquals(2, changed);
        assertEquals(300, section.get(1, 5, 2));
        assertEquals(301, section.get(15, 15, 15));
        assertEquals(CARRIER, section.get(7, 7, 7));
        assertTrue(foreign.isEmpty());
        // the other section is not visited at all
        ArraySection upper = new ArraySection();
        assertEquals(0, looks.rewriteSection(1, upper, IS_LEGACY, (x, y, z) -> foreign.add("x")));
        assertTrue(foreign.isEmpty());
    }

    @Test
    void sectionRewriteIsIdempotentAndReportsForeignStates() {
        ChunkLooks looks = looks(2, 3, 4, 300, 5, 3, 5, 301);
        ArraySection section = new ArraySection();
        section.set(2, 3, 4, CARRIER);
        section.set(5, 3, 5, STONE); // replaced by something else
        List<String> foreign = new ArrayList<>();
        assertEquals(1, looks.rewriteSection(0, section, IS_LEGACY, (x, y, z) -> foreign.add(x + "," + y + "," + z)));
        assertEquals(List.of("5,3,5"), foreign);
        assertEquals(STONE, section.get(5, 3, 5));
        // a second pass changes nothing: a look already in place is not foreign
        foreign.clear();
        assertEquals(0, looks.rewriteSection(0, section, IS_LEGACY, (x, y, z) -> foreign.add("again")));
        assertEquals(300, section.get(2, 3, 4));
        assertEquals(List.of("again"), foreign); // the stone is still reported, the rewritten look is not
    }

    @Test
    void negativeSectionsRewriteWithLocalY() {
        ChunkLooks looks = looks(4, -1, 4, 300);
        ArraySection section = new ArraySection();
        section.set(4, 15, 4, CARRIER); // y -1 is local y 15 of section -1
        assertEquals(1, looks.rewriteSection(-1, section, IS_LEGACY, (x, y, z) -> {}));
        assertEquals(300, section.get(4, 15, 4));
    }

    @Test
    void singleBlockChangeRewrite() {
        ChunkLooks looks = looks(1, 64, 1, 300);
        List<String> foreign = new ArrayList<>();
        ChunkLooks.ForeignSink sink = (x, y, z) -> foreign.add(x + "," + y + "," + z);
        assertEquals(300, looks.rewrite(1, 64, 1, CARRIER, IS_LEGACY, sink));
        assertEquals(300, looks.rewrite(1, 64, 1, LEGACY, IS_LEGACY, sink));
        assertEquals(300, looks.rewrite(1, 64, 1, 300, IS_LEGACY, sink));
        assertTrue(foreign.isEmpty());
        // not a custom block position: passed through untouched, even if it is the carrier type
        assertEquals(CARRIER, looks.rewrite(2, 64, 1, CARRIER, IS_LEGACY, sink));
        // air where a custom block is: someone broke or replaced it behind Prism's back
        assertEquals(AIR, looks.rewrite(1, 64, 1, AIR, IS_LEGACY, sink));
        assertEquals(List.of("1,64,1"), foreign);
    }

    @Test
    void manyBlocksStayConsistent() {
        Map<Long, BlockLook> map = new HashMap<>();
        for (int i = 0; i < 4000; i++) {
            int x = i & 15;
            int z = (i >> 4) & 15;
            int y = -64 + (i >> 8) * 7;
            map.put(BlockPositions.pack(x, y, z), new BlockLook(CARRIER, 1000 + i));
        }
        ChunkLooks looks = ChunkLooks.of(map);
        assertEquals(map.size(), looks.size());
        map.forEach((packed, look) -> {
            int y = (int) (packed >> 8);
            assertEquals(look, looks.at((int) ((packed >> 4) & 15), y, (int) (packed & 15)));
        });
    }

    @Test
    void displayIdsAreCarriedAndEnumerated() {
        Map<Long, BlockLook> map = new HashMap<>();
        map.put(BlockPositions.pack(1, 70, 2), new BlockLook(CARRIER, 300, 4));
        map.put(BlockPositions.pack(3, 71, 4), new BlockLook(CARRIER, 301));
        map.put(BlockPositions.pack(15, -5, 0), new BlockLook(CARRIER, 300, 9));
        ChunkLooks looks = ChunkLooks.of(map);
        assertTrue(looks.hasDisplays());
        assertTrue(looks.has(1, 70, 2));
        assertFalse(looks.has(1, 70, 3));
        assertEquals(4, looks.at(1, 70, 2).display());
        assertFalse(looks.at(3, 71, 4).displayed());
        List<String> seen = new ArrayList<>();
        looks.forEachDisplay((x, y, z, id) -> seen.add(x + "," + y + "," + z + "=" + id));
        assertEquals(List.of("15,-5,0=9", "1,70,2=4"), seen);
        assertFalse(looks(1, 70, 2, 300).hasDisplays());
    }

    private static final int SYNTHETIC = 40_000;

    /** A chunk with two custom blocks: one with a synthetic Bedrock state, one without (registered later, say). */
    private static ChunkLooks withBedrock() {
        Map<Long, BlockLook> map = new HashMap<>();
        map.put(BlockPositions.pack(1, 2, 3), new BlockLook(CARRIER, 300, BlockLook.NO_DISPLAY, SYNTHETIC));
        map.put(BlockPositions.pack(4, 5, 6), new BlockLook(CARRIER, 301));
        return ChunkLooks.of(map);
    }

    @Test
    void aJavaViewerNeverReceivesASyntheticState() {
        ChunkLooks looks = withBedrock();
        ArraySection section = new ArraySection();
        section.set(1, 2, 3, CARRIER);
        section.set(4, 5, 6, CARRIER);
        assertEquals(2, looks.rewriteSection(0, section, IS_LEGACY, (x, y, z) -> {}));
        assertEquals(300, section.get(1, 2, 3));
        assertEquals(301, section.get(4, 5, 6));
        assertEquals(300, looks.rewrite(1, 2, 3, CARRIER, IS_LEGACY, (x, y, z) -> {}));
        assertTrue(looks.hasBedrock());
        assertEquals(SYNTHETIC, looks.at(1, 2, 3).bedrock());
        assertFalse(looks.at(4, 5, 6).hasBedrock());
    }

    @Test
    void aBedrockViewerReceivesTheSyntheticStateAndTheJavaLookWhereThereIsNone() {
        ChunkLooks looks = withBedrock();
        ArraySection section = new ArraySection();
        section.set(1, 2, 3, CARRIER);
        section.set(4, 5, 6, CARRIER);
        assertEquals(2, looks.rewriteSection(0, section, IS_LEGACY, (x, y, z) -> {}, true));
        assertEquals(SYNTHETIC, section.get(1, 2, 3));
        assertEquals(301, section.get(4, 5, 6), "a look without a synthetic state falls back to the Java look");
        assertEquals(SYNTHETIC, looks.rewrite(1, 2, 3, CARRIER, IS_LEGACY, (x, y, z) -> {}, true));
        assertEquals(301, looks.rewrite(4, 5, 6, CARRIER, IS_LEGACY, (x, y, z) -> {}, true));
    }

    @Test
    void aJavaLookSentByACorrectionIsMovedToTheSyntheticStateForBedrock() {
        ChunkLooks looks = withBedrock();
        // LookReconciler and block updates send the Java look; a Geyser viewer must not see it
        assertEquals(SYNTHETIC, looks.rewrite(1, 2, 3, 300, IS_LEGACY, (x, y, z) -> {}, true));
        assertEquals(300, looks.rewrite(1, 2, 3, 300, IS_LEGACY, (x, y, z) -> {}, false));
        ArraySection section = new ArraySection();
        section.set(1, 2, 3, 300);
        assertEquals(1, looks.rewriteSection(0, section, IS_LEGACY, (x, y, z) -> {}, true));
        assertEquals(SYNTHETIC, section.get(1, 2, 3));
        assertEquals(0, looks.rewriteSection(0, section, IS_LEGACY, (x, y, z) -> {}, true), "already shown");
    }

    @Test
    void foreignStatesAreLeftAloneAndReportedForBedrockToo() {
        ChunkLooks looks = withBedrock();
        ArraySection section = new ArraySection();
        section.set(1, 2, 3, STONE);
        section.set(4, 5, 6, CARRIER);
        List<String> foreign = new ArrayList<>();
        assertEquals(1, looks.rewriteSection(0, section, IS_LEGACY, (x, y, z) -> foreign.add(x + "," + y + "," + z), true));
        assertEquals(STONE, section.get(1, 2, 3));
        assertEquals(List.of("1,2,3"), foreign);
        assertEquals(STONE, looks.rewrite(1, 2, 3, STONE, IS_LEGACY, (x, y, z) -> {}, true));
    }

    @Test
    void shownToPicksTheViewersState() {
        BlockLook look = new BlockLook(CARRIER, 300, 4, SYNTHETIC);
        assertEquals(300, look.shownTo(false));
        assertEquals(SYNTHETIC, look.shownTo(true));
        assertEquals(300, new BlockLook(CARRIER, 300).shownTo(true));
    }
}
