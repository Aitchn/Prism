package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Factories run several copies of a recipe at once and still behave like machines in a line. */
class FactoryTest {

    private static final Recipe STEP = Recipe.item("x:a", 1, "x:b", 2, 10, 10);

    private static MachineType type(int lanes) {
        List<Recipe> recipes = List.of(STEP);
        return new MachineType("f" + lanes, () -> recipes, 200_000, 20_000, 0, 0, null, lanes);
    }

    private static long ticksToFinish(Machine m) {
        long ticks = 0;
        while ((m.input() != null || m.progress() > 0) && ticks < 1_000) {
            m.energy().set(m.energy().capacity());
            m.tick();
            ticks++;
        }
        return ticks;
    }

    @Test
    void nine_lanes_do_nine_items_in_the_time_of_one() {
        Machine one = new Machine(type(1));
        one.insertInput(new Stack("x:a", 1));
        long single = ticksToFinish(one);
        Machine nine = new Machine(type(9));
        nine.insertInput(new Stack("x:a", 9));
        long batch = ticksToFinish(nine);
        assertEquals(single, batch);
        assertEquals(18, nine.output().count());
        assertEquals(2, one.output().count());
    }

    @Test
    void a_part_batch_uses_what_is_there_and_costs_in_proportion() {
        Machine four = new Machine(type(9));
        four.energy().set(100_000);
        four.insertInput(new Stack("x:a", 4));
        four.tick();
        Machine one = new Machine(type(9));
        one.energy().set(100_000);
        one.insertInput(new Stack("x:a", 1));
        one.tick();
        assertEquals(4 * (100_000 - one.energy().stored()), 100_000 - four.energy().stored());
        ticksToFinish(four);
        assertEquals(8, four.output().count());
    }

    @Test
    void a_full_output_makes_the_batch_smaller_not_stuck() {
        Machine m = new Machine(type(9));
        m.setOutput(new Stack("x:b", 50));
        m.insertInput(new Stack("x:a", 9));
        m.energy().set(100_000);
        m.tick();
        assertTrue(m.progress() > 0, "started with a smaller batch that fits in the 14 free places");
        ticksToFinish(m);
        assertEquals(64, m.output().count(), "seven copies fit: 50 + 14");
        assertEquals(2, m.input().count());
    }

    @Test
    void a_running_batch_survives_save_and_load() {
        Machine m = new Machine(type(5));
        m.energy().set(100_000);
        m.insertInput(new Stack("x:a", 5));
        m.tick();
        m.tick();
        Machine copy = new Machine(type(5));
        copy.load(m.save());
        long left = ticksToFinish(copy);
        assertTrue(left > 0);
        assertEquals(10, copy.output().count(), "all five copies finish after the reload");
        assertNull(copy.input());
    }

    @Test
    void two_factories_in_a_line_move_stacks_without_hands() {
        TestWorld w = new TestWorld();
        Pos a = TestWorld.at(0, 0, 0);
        Pos b = TestWorld.at(0, 0, 2);
        w.placeMachine(a, new MachineType("fa", () -> List.of(Recipe.item("x:a", 1, "x:b", 1, 10, 10)),
                200_000, 20_000, 0, 0, null, 9));
        w.placeMachine(b, new MachineType("fb", () -> List.of(Recipe.item("x:b", 1, "x:c", 1, 10, 10)),
                200_000, 20_000, 0, 0, null, 9));
        w.plant.transporters().add(TestWorld.at(0, 0, 1));
        w.plant.setTier(TestWorld.at(0, 0, 1), 3);
        w.plant.markDirty();
        w.machine(a).setAutoEject(true);
        w.machine(b).setAutoEject(false);
        w.machine(a).insertInput(new Stack("x:a", 64));
        for (int i = 0; i < 400; i++) {
            for (Machine m : w.plant.machines().values()) {
                m.energy().set(m.energy().capacity());
            }
            w.plant.tickNetworks();
            for (Machine m : w.plant.machines().values()) {
                m.tick();
            }
        }
        int made = (w.machine(b).output() == null ? 0 : w.machine(b).output().count())
                + (w.machine(b).input() == null ? 0 : w.machine(b).input().count());
        assertEquals(64, made, "all 64 items made it through both factories");
    }
}
