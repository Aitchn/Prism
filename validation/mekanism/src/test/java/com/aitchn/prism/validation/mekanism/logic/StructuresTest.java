package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Dynamic tanks: Prism reports boxes through valves; the tanks are shared, kept and reached by pipes. */
class StructuresTest {

    private static final Pos ORIGIN = TestWorld.at(10, 64, 10);
    private static final Pos VALVE = ORIGIN.offset(1, 1, 0);

    private static Structures.Box box(int sx, int sy, int sz) {
        return new Structures.Box(ORIGIN, ORIGIN.offset(sx - 1, sy - 1, sz - 1));
    }

    @Test
    void a_reported_box_gets_a_tank_sized_by_its_interior() {
        Plant plant = new Plant();
        assertNull(plant.structures().at(VALVE));
        plant.structures().attach(VALVE, box(3, 3, 3));
        assertEquals(16_000, plant.structures().at(VALVE).tank().capacity());
        plant.structures().attach(VALVE, box(5, 4, 3));
        assertEquals(3 * 2 * 1 * 16_000, plant.structures().at(VALVE).tank().capacity(), "a bigger box is a new tank");
        assertEquals("5x4x3", plant.structures().at(VALVE).box().size());
    }

    @Test
    void valves_of_one_box_share_one_tank() {
        Plant plant = new Plant();
        Pos other = ORIGIN.offset(1, 1, 2);
        plant.structures().attach(VALVE, box(3, 3, 3));
        plant.structures().attach(other, box(3, 3, 3));
        plant.structures().at(VALVE).tank().set(Chemical.HYDROGEN, 3_000);
        assertEquals(3_000, plant.structures().at(other).tank().amount());
        plant.structures().detach(VALVE, false);
        assertEquals(3_000, plant.structures().at(other).tank().amount(), "the other valve still holds the box");
    }

    @Test
    void content_survives_the_box_being_broken_and_built_again() {
        Plant plant = new Plant();
        plant.structures().attach(VALVE, box(3, 3, 3));
        plant.structures().at(VALVE).tank().set(Chemical.HYDROGEN, 5_000);
        plant.structures().detach(VALVE, false);
        assertNull(plant.structures().at(VALVE));
        plant.structures().attach(VALVE, box(3, 3, 3));
        assertEquals(5_000, plant.structures().at(VALVE).tank().amount());
        assertEquals(Chemical.HYDROGEN, plant.structures().at(VALVE).tank().type());
    }

    @Test
    void content_is_cut_to_the_capacity_of_a_smaller_box() {
        Plant plant = new Plant();
        plant.structures().attach(VALVE, box(5, 5, 5));
        plant.structures().at(VALVE).tank().set(Chemical.HYDROGEN, 100_000);
        plant.structures().detach(VALVE, false);
        plant.structures().attach(VALVE, box(3, 3, 3));
        assertEquals(16_000, plant.structures().at(VALVE).tank().amount());
    }

    @Test
    void content_comes_back_from_the_valves_saved_text_after_a_restart() {
        Plant plant = new Plant();
        plant.structures().attach(VALVE, box(3, 3, 3));
        plant.structures().at(VALVE).tank().set(Chemical.OXYGEN, 7_000);
        String saved = plant.structures().contentOf(VALVE);
        Plant fresh = new Plant();
        fresh.structures().restore(VALVE, saved);
        fresh.structures().attach(VALVE, box(3, 3, 3));
        assertEquals(7_000, fresh.structures().at(VALVE).tank().amount());
        assertEquals(Chemical.OXYGEN, fresh.structures().at(VALVE).tank().type());
    }

    @Test
    void a_loaded_snapshot_is_used_once_so_a_drained_tank_stays_empty() {
        Plant plant = new Plant();
        plant.structures().restore(VALVE, "hydrogen,5000");
        plant.structures().attach(VALVE, box(3, 3, 3));
        assertEquals(5_000, plant.structures().at(VALVE).tank().amount());
        plant.structures().at(VALVE).tank().set(null, 0);
        plant.structures().detach(VALVE, false);
        plant.structures().attach(VALVE, box(3, 3, 3));
        assertEquals(0, plant.structures().at(VALVE).tank().amount());
        assertNull(plant.structures().contentOf(VALVE));
    }

    @Test
    void a_broken_box_still_lets_its_valves_save_what_it_held() {
        Plant plant = new Plant();
        plant.structures().attach(VALVE, box(3, 3, 3));
        plant.structures().at(VALVE).tank().set(Chemical.HYDROGEN, 2_500);
        plant.structures().detach(VALVE, false);
        assertEquals("hydrogen,2500", plant.structures().contentOf(VALVE));
        plant.structures().detach(VALVE, true);
        assertNull(plant.structures().contentOf(VALVE), "an unloaded valve leaves nothing behind in memory");
    }

    @Test
    void pipes_fill_the_tank_through_a_valve_and_drain_it_into_another_machine() {
        MachineType store = new MachineType("chemical_tank", List::of, 1, 1, 64_000, 0, c -> true);
        TestWorld w = new TestWorld();
        Pos valve = ORIGIN.offset(1, 1, 0);
        w.plant.structures().attach(valve, box(3, 3, 3));
        Pos source = ORIGIN.offset(1, 1, -2);
        w.placeMachine(source, store);
        w.placePipe(ORIGIN.offset(1, 1, -1));
        w.machine(source).inTank().set(Chemical.HYDROGEN, 4_000);
        for (int i = 0; i < 40; i++) {
            w.plant.tickNetworks();
        }
        Structures.Formed f = w.plant.structures().at(valve);
        assertEquals(4_000, f.tank().amount() + w.machine(source).inTank().amount(), "nothing lost");
        assertTrue(f.tank().amount() > 0, "the tank took some through the valve");
        assertEquals(Chemical.HYDROGEN, f.tank().type());
    }

    @Test
    void a_wall_block_that_is_not_a_valve_lets_nothing_through() {
        MachineType store = new MachineType("chemical_tank", List::of, 1, 1, 64_000, 0, c -> true);
        TestWorld w = new TestWorld();
        w.plant.structures().attach(ORIGIN.offset(1, 1, 2), box(3, 3, 3)); // the valve is on the far side
        Pos source = ORIGIN.offset(1, 1, -2);
        w.placeMachine(source, store);
        w.placePipe(ORIGIN.offset(1, 1, -1));
        w.machine(source).inTank().set(Chemical.HYDROGEN, 1_000);
        for (int i = 0; i < 10; i++) {
            w.plant.tickNetworks();
        }
        assertEquals(0, w.plant.structures().at(ORIGIN.offset(1, 1, 2)).tank().amount());
        assertEquals(1_000, w.machine(source).inTank().amount());
    }
}
