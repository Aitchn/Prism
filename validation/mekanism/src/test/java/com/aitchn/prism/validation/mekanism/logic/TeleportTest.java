package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Teleporter frequencies, turns and costs, and that the tuning lives in the machine's saved state. */
class TeleportTest {

    private static final Pos A = TestWorld.at(0, 64, 0);
    private static final Pos B = TestWorld.at(100, 64, 0);
    private static final Pos C = TestWorld.at(0, 64, 300);

    @Test
    void a_teleporter_visits_the_others_in_turn_and_never_itself() {
        List<Pos> all = List.of(C, A, B);
        assertEquals(C, Teleport.pick(A, all, 0));
        assertEquals(B, Teleport.pick(A, all, 1));
        assertEquals(C, Teleport.pick(A, all, 2));
        assertNull(Teleport.pick(A, List.of(A), 0), "alone, nowhere to go");
    }

    @Test
    void the_trip_costs_more_the_further_it_goes_and_a_lot_for_another_world() {
        assertTrue(Teleport.cost(A, C) > Teleport.cost(A, B));
        assertEquals(Teleport.BASE_COST + 100 * Teleport.PER_BLOCK, Teleport.cost(A, B));
        assertEquals(Teleport.OTHER_WORLD, Teleport.cost(A, new Pos("nether", 0, 64, 0)));
    }

    @Test
    void tuning_round_trips_and_odd_text_means_the_public_frequency() {
        Teleport.Tuning t = new Teleport.Tuning("home: base", 4);
        assertEquals(t, Teleport.Tuning.parse(t.save()));
        assertEquals(new Teleport.Tuning("", 0), Teleport.Tuning.parse(null));
        assertEquals(new Teleport.Tuning("", 0), Teleport.Tuning.parse("r8:12"));
        assertEquals(5, t.next().counter());
        assertEquals("mine", t.tuned("mine").frequency());
    }

    @Test
    void tuning_is_kept_when_the_machine_is_saved_picked_up_and_loaded() {
        MachineType type = MachineType.fixed("teleporter", List.of(), 200_000, 20_000, 0);
        Machine machine = new Machine(type);
        machine.setAttachment(new Teleport.Tuning("home", 2).save());
        for (String text : List.of(machine.save(), machine.saveSettings())) {
            Machine copy = new Machine(type);
            copy.load(text);
            assertEquals(new Teleport.Tuning("home", 2), Teleport.Tuning.parse(copy.attachment()));
        }
    }

    @Test
    void a_teleporter_fed_by_a_cable_can_pay_for_a_trip() {
        TestWorld w = new TestWorld();
        MachineType type = MachineType.fixed("teleporter", List.of(), 200_000, 20_000, 0);
        w.placeMachine(TestWorld.at(0, 0, 0), type);
        w.placeSolar(TestWorld.at(2, 0, 0));
        w.placeCable(TestWorld.at(1, 0, 0));
        long need = Teleport.cost(A, B);
        int ticks = 0;
        while (w.machine(TestWorld.at(0, 0, 0)).energy().stored() < need && ticks < 20_000) {
            w.tick();
            ticks++;
        }
        assertTrue(w.machine(TestWorld.at(0, 0, 0)).energy().consume(need));
    }
}
