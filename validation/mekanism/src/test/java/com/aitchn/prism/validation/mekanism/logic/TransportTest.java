package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Transporters, auto-eject, item faces, redstone and tiers working together on real machines. */
class TransportTest {

    private static final Pos A = TestWorld.at(0, 0, 0);
    private static final Pos B = TestWorld.at(0, 0, 2);
    private static final Pos C = TestWorld.at(0, 0, 4);

    private static MachineType step(String id, String in, String out) {
        return MachineType.fixed(id, List.of(Recipe.item(in, 1, out, 1, 10, 10)), 20_000, 2_000, 0);
    }

    private static void run(TestWorld w, int ticks) {
        for (int i = 0; i < ticks; i++) {
            for (Machine m : w.plant.machines().values()) {
                m.energy().set(m.energy().capacity());
            }
            w.plant.tickNetworks();
            for (Machine m : w.plant.machines().values()) {
                m.tick();
            }
            w.ticks++;
        }
    }

    private static int total(Machine m, String id) {
        int sum = 0;
        for (Stack s : new Stack[] {m.input(), m.extra(), m.output()}) {
            if (s != null && s.id().equals(id)) {
                sum += s.count();
            }
        }
        return sum;
    }

    /** A -> transporter -> B -> transporter -> C: three machines, nothing carried by hand. */
    private static TestWorld line() {
        TestWorld w = new TestWorld();
        w.placeMachine(A, step("a", "x:a", "x:b"));
        w.placeMachine(B, step("b", "x:b", "x:c"));
        w.placeMachine(C, step("c", "x:c", "x:d"));
        w.plant.transporters().add(TestWorld.at(0, 0, 1));
        w.plant.transporters().add(TestWorld.at(0, 0, 3));
        w.plant.markDirty();
        return w;
    }

    @Test
    void items_walk_down_a_line_of_machines_without_hands() {
        TestWorld w = line();
        w.machine(A).setAutoEject(true);
        w.machine(B).setAutoEject(true);
        w.machine(C).setAutoEject(true);
        w.machine(A).insertInput(new Stack("x:a", 20));
        run(w, 1_500);
        assertEquals(20, total(w.machine(C), "x:d"), "all twenty items went through three machines");
        assertNull(w.machine(A).input());
    }

    @Test
    void without_auto_eject_nothing_leaves() {
        TestWorld w = line();
        w.machine(A).insertInput(new Stack("x:a", 4));
        run(w, 300);
        assertEquals(4, total(w.machine(A), "x:b"));
        assertNull(w.machine(B).input());
    }

    @Test
    void an_off_face_neither_gives_nor_takes() {
        TestWorld w = line();
        w.machine(A).setAutoEject(true);
        w.machine(A).insertInput(new Stack("x:a", 3));
        w.machine(B).setItemFace(5, Access.OFF); // the north face of B touches the transporter at (0,0,1)
        run(w, 200);
        assertEquals(3, total(w.machine(A), "x:b"), "B refuses items on that face");
        w.machine(B).setItemFace(5, Access.ALL);
        run(w, 200);
        assertTrue(total(w.machine(B), "x:b") + total(w.machine(B), "x:c") == 3);
    }

    @Test
    void an_open_menu_keeps_transporters_away_from_the_output_slot() {
        TestWorld w = line();
        Machine a = w.machine(A);
        a.setAutoEject(true);
        a.setOutput(new Stack("x:b", 5));
        a.setMenuOpen(true);
        run(w, 20);
        assertEquals(5, a.output().count(), "nothing leaves while someone looks at the slot");
        a.setMenuOpen(false);
        run(w, 20);
        assertTrue(a.output() == null || a.output().count() < 5);
    }

    @Test
    void redstone_modes_gate_work() {
        TestWorld w = line();
        Machine a = w.machine(A);
        a.insertInput(new Stack("x:a", 1));
        a.setRedstone(Redstone.HIGH);
        run(w, 100);
        assertEquals(1, total(a, "x:a"), "no signal, no work");
        a.setPowered(true);
        run(w, 100);
        assertEquals(1, total(a, "x:b"));
        Machine b = w.machine(B);
        b.insertInput(new Stack("x:b", 1));
        b.setRedstone(Redstone.LOW);
        b.setPowered(true);
        run(w, 100);
        assertEquals(1, total(b, "x:b"), "inverted mode stops with a signal");
        b.setPowered(false);
        run(w, 100);
        assertEquals(1, total(b, "x:c"));
    }

    @Test
    void colours_split_networks_but_each_still_works() {
        TestWorld w = line();
        w.plant.setColour(TestWorld.at(0, 0, 1), 3);
        w.plant.rebuildIfNeeded();
        assertEquals(2, w.plant.itemNets().size(), "a coloured and an uncoloured transporter do not join");
        w.machine(A).setAutoEject(true);
        w.machine(A).insertInput(new Stack("x:a", 2));
        run(w, 200);
        assertEquals(2, total(w.machine(B), "x:b") + total(w.machine(B), "x:c"));
    }

    @Test
    void tiers_raise_the_item_budget() {
        TestWorld basic = new TestWorld();
        TestWorld ultimate = new TestWorld();
        for (TestWorld w : List.of(basic, ultimate)) {
            w.placeMachine(A, MachineType.fixed("a", List.of(Recipe.item("x:a", 1, "x:b", 64, 1, 1)), 20_000, 2_000, 0));
            w.placeMachine(B, step("b", "x:b", "x:c"));
            w.plant.transporters().add(TestWorld.at(0, 0, 1));
            w.machine(A).setAutoEject(true);
            w.machine(A).insertInput(new Stack("x:a", 1));
        }
        ultimate.plant.setTier(TestWorld.at(0, 0, 1), 3);
        run(basic, 4);
        run(ultimate, 4);
        assertTrue(total(ultimate.machine(B), "x:b") + total(ultimate.machine(B), "x:c")
                > total(basic.machine(B), "x:b") + total(basic.machine(B), "x:c"), "faster transporter moves more per tick");
    }

    @Test
    void settings_survive_a_save_and_a_load() {
        Machine m = new Machine(step("a", "x:a", "x:b"));
        m.setAutoEject(true);
        m.setRedstone(Redstone.LOW);
        m.setItemFace(2, Access.OUTPUT);
        Machine copy = new Machine(step("a", "x:a", "x:b"));
        copy.load(m.saveSettings());
        assertTrue(copy.autoEject());
        assertEquals(Redstone.LOW, copy.redstone());
        assertEquals(Access.OUTPUT, copy.itemFace(2));
        assertEquals(Access.ALL, copy.itemFace(0));
    }
}
