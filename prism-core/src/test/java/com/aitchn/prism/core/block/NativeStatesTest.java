package com.aitchn.prism.core.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.core.data.SqliteDataService;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void allocationIsStableAcrossRestartsAndNeverReusesIndexZero(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("d.db");
        PrismKey a = PrismKey.parse("m:a");
        PrismKey b = PrismKey.parse("m:b");
        try (var service = new SqliteDataService(file)) {
            var alloc = new StateAllocator(service.store("prism"));
            assertEquals(1, alloc.allocate(a).getAsInt());
            assertEquals(2, alloc.allocate(b).getAsInt());
            assertEquals(1, alloc.allocate(a).getAsInt());
        }
        try (var service = new SqliteDataService(file)) {
            var alloc = new StateAllocator(service.store("prism"));
            assertEquals(2, alloc.allocate(b).getAsInt());
            assertEquals(a, alloc.keyFor(1).orElseThrow());
            // a new block does not take over a state while another mod's block is absent
            assertEquals(3, alloc.allocate(PrismKey.parse("m:c")).getAsInt());
        }
    }

    @Test
    void runsOutAfter799() throws Exception {
        try (var service = new SqliteDataService(null)) {
            var alloc = new StateAllocator(service.store("prism"));
            for (int i = 0; i < 799; i++) {
                assertTrue(alloc.allocate(new PrismKey("m", "b" + i)).isPresent());
            }
            assertTrue(alloc.allocate(new PrismKey("m", "extra")).isEmpty());
        }
    }
}
