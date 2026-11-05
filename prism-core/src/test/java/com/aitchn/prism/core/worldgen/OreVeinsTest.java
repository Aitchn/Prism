package com.aitchn.prism.core.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OreVeinsTest {

    private static final OreVeins.Spec SPEC = new OreVeins.Spec(6, 1, 8, -16, 48);

    @Test
    void sameInputsGiveSameVeins() {
        assertEquals(OreVeins.positions(SPEC, 42, 7, 3, -5, -64, 320),
                OreVeins.positions(SPEC, 42, 7, 3, -5, -64, 320));
        assertNotEquals(OreVeins.positions(SPEC, 42, 7, 3, -5, -64, 320),
                OreVeins.positions(SPEC, 42, 7, 4, -5, -64, 320));
        assertNotEquals(OreVeins.positions(SPEC, 42, 7, 3, -5, -64, 320),
                OreVeins.positions(SPEC, 42, 8, 3, -5, -64, 320));
    }

    @Test
    void staysInsideChunkAndHeightRange() {
        for (int chunk = -20; chunk < 20; chunk++) {
            List<OreVeins.Position> positions = OreVeins.positions(SPEC, 1, 2, chunk, chunk * 3, -64, 320);
            assertFalse(positions.isEmpty());
            assertTrue(positions.size() <= 6 * 8);
            for (OreVeins.Position p : positions) {
                assertTrue(p.x() >= 0 && p.x() < 16 && p.z() >= 0 && p.z() < 16, p.toString());
                assertTrue(p.y() >= -16 && p.y() <= 48, p.toString());
            }
        }
    }

    @Test
    void clampsToWorldAndSkipsRangesOutsideIt() {
        OreVeins.Spec low = new OreVeins.Spec(4, 1, 8, -200, 10);
        for (OreVeins.Position p : OreVeins.positions(low, 1, 2, 0, 0, 0, 256)) {
            assertTrue(p.y() >= 0 && p.y() <= 10, p.toString());
        }
        assertTrue(OreVeins.positions(new OreVeins.Spec(4, 1, 8, 300, 400), 1, 2, 0, 0, 0, 256).isEmpty());
        // A one-block range still works.
        for (OreVeins.Position p : OreVeins.positions(new OreVeins.Spec(2, 1, 5, 5, 5), 1, 2, 0, 0, 0, 256)) {
            assertEquals(5, p.y());
        }
    }

    @Test
    void rarityLeavesMostChunksEmpty() {
        OreVeins.Spec rare = new OreVeins.Spec(4, 10, 8, 0, 40);
        int filled = 0;
        for (int chunk = 0; chunk < 1000; chunk++) {
            if (!OreVeins.positions(rare, 9, 2, chunk, -chunk, -64, 320).isEmpty()) {
                filled++;
            }
        }
        assertTrue(filled > 50 && filled < 160, "filled " + filled);
    }
}
