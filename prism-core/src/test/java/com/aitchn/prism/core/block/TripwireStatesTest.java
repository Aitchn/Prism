package com.aitchn.prism.core.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TripwireStatesTest {

    @Test
    void sixtyFourDistinctDisarmedStates() {
        Set<String> variants = new HashSet<>();
        for (int id = TripwireStates.OFFSET; id < TripwireStates.OFFSET + TripwireStates.COUNT; id++) {
            String variant = TripwireStates.state(id).variant();
            assertTrue(variant.contains("disarmed=true"), variant);
            variants.add(variant);
        }
        assertEquals(64, variants.size());
        assertFalse(TripwireStates.isTripwire(799));
        assertFalse(TripwireStates.isTripwire(TripwireStates.OFFSET + 64));
        assertEquals("attached=false,disarmed=true,east=false,north=false,powered=false,south=false,west=false",
                TripwireStates.state(TripwireStates.OFFSET).variant());
        for (int id = TripwireStates.OFFSET; id < TripwireStates.OFFSET + 4; id++) {
            var state = TripwireStates.state(id);
            assertFalse(state.east() || state.north() || state.south() || state.west(), state.toString());
        }
    }

    @Test
    void vanillaModelsFollowTheGameTable() {
        assertEquals(new TripwireStates.VanillaModel("minecraft:block/tripwire_ns", 0),
                TripwireStates.vanilla(false, false, false, false, false));
        assertEquals(new TripwireStates.VanillaModel("minecraft:block/tripwire_n", 270),
                TripwireStates.vanilla(false, false, false, false, true));
        assertEquals(new TripwireStates.VanillaModel("minecraft:block/tripwire_ns", 90),
                TripwireStates.vanilla(false, true, false, false, true));
        assertEquals(new TripwireStates.VanillaModel("minecraft:block/tripwire_ne", 180),
                TripwireStates.vanilla(false, false, false, true, true));
        assertEquals(new TripwireStates.VanillaModel("minecraft:block/tripwire_nse", 180),
                TripwireStates.vanilla(false, false, true, true, true));
        assertEquals(new TripwireStates.VanillaModel("minecraft:block/tripwire_attached_nsew", 0),
                TripwireStates.vanilla(true, true, true, true, true));
    }
}
