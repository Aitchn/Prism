package com.aitchn.prism.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.core.block.NoteBlockStates;
import com.aitchn.prism.core.content.YamlFiles;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class NativeBlockstatesTest {

    @Test
    void listsAll800StatesAndMapsAllocatedOnes() throws Exception {
        String json = NativeBlockstates.noteBlock(Map.of(
                1, new NativeBlockstates.StateModel(PrismKey.parse("m:custom/shiny"), 0, 0),
                3, new NativeBlockstates.StateModel(PrismKey.parse("m:block/press"), 0, 90)));
        Map<String, Object> variants = (Map<String, Object>) ((Map<String, Object>) YamlFiles.parse("t", json))
                .get("variants");
        assertEquals(NoteBlockStates.COUNT + NativeBlockstates.HEAD_INSTRUMENTS.size() * NoteBlockStates.NOTES * 2,
                variants.size());
        assertEquals("minecraft:block/note_block",
                ((Map<String, Object>) variants.get("instrument=zombie,note=3,powered=false")).get("model"));
        assertEquals("m:custom/shiny", ((Map<String, Object>) variants.get(NoteBlockStates.state(1).variant())).get("model"));
        assertEquals(90, ((Map<String, Object>) variants.get(NoteBlockStates.state(3).variant())).get("y"));
        assertEquals("minecraft:block/note_block",
                ((Map<String, Object>) variants.get(NoteBlockStates.state(2).variant())).get("model"));
        assertTrue(variants.containsKey("instrument=pling,note=24,powered=true"));
    }

    @Test
    void tripwireKeepsVanillaStringAndMapsAllocatedStates() throws Exception {
        int id = com.aitchn.prism.core.block.TripwireStates.OFFSET + 5;
        String json = NativeBlockstates.tripwire(Map.of(
                id, new NativeBlockstates.StateModel(PrismKey.parse("m:block/flower"), 0, 0),
                7, new NativeBlockstates.StateModel(PrismKey.parse("m:block/ignored"), 0, 0)));
        Map<String, Object> variants = (Map<String, Object>) ((Map<String, Object>) YamlFiles.parse("t", json))
                .get("variants");
        assertEquals(128, variants.size());
        assertEquals("m:block/flower", ((Map<String, Object>) variants.get(
                com.aitchn.prism.core.block.TripwireStates.state(id).variant())).get("model"));
        Map<String, Object> string = (Map<String, Object>) variants.get(
                "attached=false,disarmed=false,east=true,north=false,powered=true,south=false,west=false");
        assertEquals("minecraft:block/tripwire_n", string.get("model"));
        assertEquals(90, string.get("y"));
        assertTrue(!json.contains("m:block/ignored"));
    }
}
