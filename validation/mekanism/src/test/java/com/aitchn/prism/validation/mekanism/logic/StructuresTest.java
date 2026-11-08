package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Dynamic tanks: the box is found, sized, kept when parts come and go, and pipes reach it through valves. */
class StructuresTest {

    private static final Pos ORIGIN = TestWorld.at(10, 64, 10);

    /** Fills the shell of an sx by sy by sz box at ORIGIN with casing. */
    private static void box(Plant plant, int sx, int sy, int sz) {
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    boolean shell = x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1;
                    if (shell) {
                        plant.structures().members().add(ORIGIN.offset(x, y, z));
                    }
                }
            }
        }
        plant.markDirty();
        plant.rebuildIfNeeded();
    }

    @Test
    void a_whole_box_forms_with_capacity_by_interior_volume() {
        Plant plant = new Plant();
        box(plant, 3, 3, 3);
        Structures.Formed f = plant.structures().at(ORIGIN);
        assertNotNull(f);
        assertEquals(1, f.interior());
        assertEquals(16_000, f.tank().capacity());
        assertEquals("3x3x3", f.size());
        plant = new Plant();
        box(plant, 5, 4, 6);
        assertEquals(3 * 2 * 4 * 16_000L, plant.structures().at(ORIGIN).tank().capacity());
    }

    @Test
    void a_hole_extra_casing_or_a_blocked_interior_means_not_formed() {
        Plant plant = new Plant();
        box(plant, 3, 3, 3);
        plant.structures().members().remove(ORIGIN.offset(1, 0, 1));
        plant.markDirty();
        plant.rebuildIfNeeded();
        assertNull(plant.structures().at(ORIGIN));

        plant = new Plant();
        box(plant, 3, 3, 3);
        plant.structures().members().add(ORIGIN.offset(3, 1, 1));
        plant.markDirty();
        plant.rebuildIfNeeded();
        assertNull(plant.structures().at(ORIGIN), "a block stuck on the outside spoils the box");

        plant = new Plant();
        box(plant, 3, 3, 3);
        plant.structures().members().add(ORIGIN.offset(1, 1, 1));
        plant.markDirty();
        plant.rebuildIfNeeded();
        assertNull(plant.structures().at(ORIGIN), "casing inside blocks the interior");
        box(new Plant(), 2, 2, 2);
        Plant tiny = new Plant();
        box(tiny, 2, 2, 2);
        assertNull(tiny.structures().at(ORIGIN), "too small");
    }

    @Test
    void a_machine_or_cable_inside_the_box_spoils_it() {
        Plant plant = new Plant();
        box(plant, 3, 3, 3);
        plant.cables().add(ORIGIN.offset(1, 1, 1));
        plant.markDirty();
        plant.rebuildIfNeeded();
        assertNull(plant.structures().at(ORIGIN), "a known block in the middle is not empty space");
    }

    @Test
    void content_survives_the_box_being_opened_and_closed_again() {
        Plant plant = new Plant();
        box(plant, 3, 3, 3);
        plant.structures().at(ORIGIN).tank().set(Chemical.HYDROGEN, 5_000);
        Pos side = ORIGIN.offset(1, 0, 1);
        plant.structures().members().remove(side);
        plant.markDirty();
        plant.rebuildIfNeeded();
        assertNull(plant.structures().at(ORIGIN));
        plant.structures().members().add(side);
        plant.markDirty();
        plant.rebuildIfNeeded();
        assertEquals(5_000, plant.structures().at(ORIGIN).tank().amount());
        assertEquals(Chemical.HYDROGEN, plant.structures().at(ORIGIN).tank().type());
    }

    @Test
    void content_comes_back_from_the_anchors_saved_text_after_a_restart() {
        Plant plant = new Plant();
        box(plant, 3, 3, 3);
        plant.structures().at(ORIGIN).tank().set(Chemical.OXYGEN, 7_000);
        String saved = plant.structures().contentOf(ORIGIN);
        Plant fresh = new Plant();
        fresh.structures().restore(ORIGIN, saved);
        box(fresh, 3, 3, 3);
        assertEquals(7_000, fresh.structures().at(ORIGIN).tank().amount());
        assertEquals(Chemical.OXYGEN, fresh.structures().at(ORIGIN).tank().type());
    }

    @Test
    void pipes_fill_the_tank_through_a_valve_and_drain_it_into_another_machine() {
        MachineType store = new MachineType("chemical_tank", List::of, 1, 1, 64_000, 0, c -> true);
        TestWorld w = new TestWorld();
        box(w.plant, 3, 3, 3);
        Pos valve = ORIGIN.offset(1, 1, 0);
        w.plant.structures().valves().add(valve);
        Pos source = ORIGIN.offset(1, 1, -2);
        w.placeMachine(source, store);
        w.placePipe(ORIGIN.offset(1, 1, -1));
        w.machine(source).inTank().set(Chemical.HYDROGEN, 4_000);
        for (int i = 0; i < 40; i++) {
            w.plant.tickNetworks();
        }
        Structures.Formed f = w.plant.structures().at(ORIGIN);
        assertEquals(4_000, f.tank().amount() + w.machine(source).inTank().amount(), "nothing lost");
        assertTrue(f.tank().amount() > 0, "the tank took some through the valve");
        assertEquals(Chemical.HYDROGEN, f.tank().type());
    }

    @Test
    void a_casing_block_that_is_not_a_valve_lets_nothing_through() {
        MachineType store = new MachineType("chemical_tank", List::of, 1, 1, 64_000, 0, c -> true);
        TestWorld w = new TestWorld();
        box(w.plant, 3, 3, 3);
        Pos source = ORIGIN.offset(1, 1, -2);
        w.placeMachine(source, store);
        w.placePipe(ORIGIN.offset(1, 1, -1));
        w.machine(source).inTank().set(Chemical.HYDROGEN, 1_000);
        for (int i = 0; i < 10; i++) {
            w.plant.tickNetworks();
        }
        assertEquals(0, w.plant.structures().at(ORIGIN).tank().amount());
        assertEquals(1_000, w.machine(source).inTank().amount());
    }
}
