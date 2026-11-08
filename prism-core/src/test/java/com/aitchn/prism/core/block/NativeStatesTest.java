package com.aitchn.prism.core.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class NativeStatesTest {

    @Test
    void indexAndStateRoundTripForAll800() {
        var seen = new HashSet<String>();
        for (int i = 0; i < NoteBlockStates.COUNT; i++) {
            var s = NoteBlockStates.state(i);
            assertEquals(i, NoteBlockStates.index(s.instrument(), s.note(), s.powered()));
            assertTrue(seen.add(s.variant()));
        }
        assertEquals(800, seen.size());
        assertEquals("instrument=harp,note=0,powered=false", NoteBlockStates.state(0).variant());
        assertEquals(-1, NoteBlockStates.index("kazoo", 0, false));
        assertEquals(-1, NoteBlockStates.index("harp", 25, false));
    }

    @Test
    void looksCoverVariantsAndFacings() {
        var block = new com.aitchn.prism.api.content.BlockDefinition(com.aitchn.prism.api.PrismKey.parse("m:press"),
                "mod", com.aitchn.prism.api.PrismKey.parse("minecraft:barrier"), java.util.Optional.empty(),
                com.aitchn.prism.api.content.ComponentMap.builder()
                        .put(com.aitchn.prism.api.content.PrismComponents.ROTATION,
                                com.aitchn.prism.api.content.PrismComponents.Rotation.HORIZONTAL)
                        .put(com.aitchn.prism.api.content.PrismComponents.VARIANTS,
                                new com.aitchn.prism.api.content.PrismComponents.Variants(java.util.List.of("idle", "on")))
                        .build(),
                java.util.List.of());
        var looks = Appearances.looks(block);
        assertEquals(8, looks.size());
        assertEquals("m:press#idle", looks.get(0).id());
        assertEquals("m:press#on@west", looks.get(7).id());
        assertEquals("m:block/press_on", Appearances.model(block, "on").toString());
        assertEquals(270, Appearances.rotation("WEST")[1]);
    }
}
