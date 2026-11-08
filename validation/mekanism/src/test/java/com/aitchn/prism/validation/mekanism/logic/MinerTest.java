package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The digital miner's scan, its ore filter, and how its drops and scan position live in a machine. */
class MinerTest {

    @Test
    void the_scan_visits_every_position_once_layer_by_layer_and_then_stops() {
        Miner miner = new Miner();
        miner.setRadius(1);
        Set<List<Integer>> seen = new HashSet<>();
        int lastDepth = 0;
        int[] offset;
        while ((offset = miner.next(3)) != null) {
            assertTrue(seen.add(List.of(offset[0], offset[1], offset[2])), "visited twice");
            assertTrue(-offset[1] >= lastDepth, "a layer is finished before the next one starts");
            lastDepth = -offset[1];
            miner.advance();
        }
        assertEquals(3 * 3 * 3, seen.size());
        assertTrue(miner.done(3));
        assertNull(miner.next(3));
    }

    @Test
    void only_ores_are_taken() {
        assertTrue(Miner.accepts("minecraft:iron_ore"));
        assertTrue(Miner.accepts("minecraft:deepslate_diamond_ore"));
        assertTrue(Miner.accepts("minecraft:ancient_debris"));
        assertFalse(Miner.accepts("minecraft:stone"));
        assertFalse(Miner.accepts("minecraft:chest"));
    }

    @Test
    void radius_cycles_through_the_four_sizes_and_restarts_the_scan() {
        Miner miner = new Miner();
        miner.advance();
        assertEquals(12, miner.nextRadius());
        assertEquals(0, miner.index());
        assertEquals(16, miner.nextRadius());
        assertEquals(4, miner.nextRadius());
    }

    @Test
    void the_scan_position_survives_a_machine_save_and_load() {
        MachineType type = MachineType.fixed("digital_miner", List.of(), 100_000, 10_000, 0);
        Machine machine = new Machine(type);
        Miner miner = new Miner();
        miner.setRadius(12);
        for (int i = 0; i < 77; i++) {
            miner.advance();
        }
        machine.setAttachment(miner.save());
        for (String text : List.of(machine.save(), machine.saveSettings())) {
            Machine copy = new Machine(type);
            copy.load(text);
            Miner again = new Miner();
            again.load(copy.attachment());
            assertEquals(12, again.radius());
            assertEquals(77, again.index());
        }
    }

    @Test
    void drops_fill_the_output_slot_of_the_same_item_and_the_rest_spills() {
        Machine machine = new Machine(MachineType.fixed("digital_miner", List.of(), 1, 1, 0));
        assertEquals(0, machine.insertOutput(new Stack("minecraft:raw_iron", 40)).count());
        Stack rest = machine.insertOutput(new Stack("minecraft:raw_iron", 40));
        assertEquals(16, rest.count());
        assertEquals(64, machine.output().count());
        assertEquals(5, machine.insertOutput(new Stack("minecraft:coal", 5)).count(), "another item does not mix");
        machine.setMenuOpen(true);
        machine.setOutput(null);
        assertEquals(3, machine.insertOutput(new Stack("minecraft:coal", 3)).count(), "nothing is added while a menu shows the slot");
    }

    @Test
    void a_scan_with_pending_positions_waits_for_them() {
        Miner miner = new Miner();
        miner.begin(3);
        assertEquals(3, miner.pending());
        miner.finish();
        miner.finish();
        miner.finish();
        miner.finish();
        assertEquals(0, miner.pending());
    }
}
