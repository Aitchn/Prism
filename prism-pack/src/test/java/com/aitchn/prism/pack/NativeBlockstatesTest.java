package com.aitchn.prism.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.core.block.NoteBlockStates;
import com.aitchn.prism.core.content.YamlFiles;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class NativeBlockstatesTest {

    @Test
    void listsAll800StatesAndMapsAllocatedOnes() throws Exception {
        PrismKey ruby = PrismKey.parse("m:ruby");
        PrismKey bare = PrismKey.parse("m:bare");
        String json = NativeBlockstates.noteBlock(Map.of(ruby, 1, bare, 2), Set.of(ruby));
        Map<String, Object> variants = (Map<String, Object>) ((Map<String, Object>) YamlFiles.parse("t", json))
                .get("variants");
        assertEquals(NoteBlockStates.COUNT, variants.size());
        assertEquals("m:block/ruby", ((Map<String, Object>) variants.get(NoteBlockStates.state(1).variant())).get("model"));
        // allocated but without appearance, and unallocated, keep the vanilla look
        assertEquals("minecraft:block/note_block",
                ((Map<String, Object>) variants.get(NoteBlockStates.state(2).variant())).get("model"));
        assertTrue(variants.containsKey("instrument=pling,note=24,powered=true"));
    }
}
