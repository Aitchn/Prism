package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Chemical pipes: which face feeds which tank, fair sharing, storage tanks, and that faces survive saving. */
class GasTest {

    private static final Chemical X = new Chemical("x");

    /** A two-gas machine: hydrogen (first tank) and oxygen (second tank) make x. */
    private static MachineType infuser() {
        return MachineType.fixed("infuser", List.of(Recipe.chemicals(null, null, Chemical.HYDROGEN, 10,
                Chemical.OXYGEN, 10, X, 10, 10, 1)), 1_000, 1_000, 1_000);
    }

    private static TestWorld pair(Side faceOnSeparator, Side faceOnInfuser) {
        TestWorld w = new TestWorld();
        Pos separator = TestWorld.at(0, 0, 0);
        Pos infuser = TestWorld.at(4, 0, 0);
        w.placeMachine(separator, TestMachines.separator());
        w.placeMachine(infuser, infuser());
        for (int x = 1; x <= 3; x++) {
            w.placePipe(TestWorld.at(x, 0, 0));
        }
        w.machine(separator).setFace(0, faceOnSeparator);
        w.machine(infuser).setFace(1, faceOnInfuser);
        w.machine(separator).energy().set(100_000);
        w.machine(separator).inTank().set(Chemical.WATER, 100);
        return w;
    }

    @Test
    void aFaceOnTheFirstTankOnlyTakesTheFirstGas() {
        TestWorld w = pair(Side.ALL, Side.IN1);
        w.tick(200);
        Machine infuser = w.machine(TestWorld.at(4, 0, 0));
        assertEquals(Chemical.HYDROGEN, infuser.inTank().type());
        assertTrue(infuser.inTank().amount() > 0);
        assertEquals(0, infuser.inTank2().amount(), "oxygen is not accepted by the first tank");
    }

    @Test
    void aFaceOnTheSecondTankOnlyTakesTheSecondGas() {
        TestWorld w = pair(Side.ALL, Side.IN2);
        w.tick(200);
        Machine infuser = w.machine(TestWorld.at(4, 0, 0));
        assertEquals(Chemical.OXYGEN, infuser.inTank2().type());
        assertEquals(0, infuser.inTank().amount());
    }

    @Test
    void anOffFaceNeitherGivesNorTakes() {
        TestWorld w = pair(Side.ALL, Side.OFF);
        w.tick(200);
        Machine infuser = w.machine(TestWorld.at(4, 0, 0));
        assertEquals(0, infuser.inTank().amount() + infuser.inTank2().amount());
        w = pair(Side.OFF, Side.ALL);
        w.tick(200);
        infuser = w.machine(TestWorld.at(4, 0, 0));
        assertEquals(0, infuser.inTank().amount() + infuser.inTank2().amount());
    }

    @Test
    void aSourceSharesWhatItHasBetweenTwoThirstyMachines() {
        TestWorld w = new TestWorld();
        Pos source = TestWorld.at(0, 0, 0);
        Pos left = TestWorld.at(-4, 0, 0);
        Pos right = TestWorld.at(4, 0, 0);
        w.placeMachine(source, TestMachines.separator());
        w.placeMachine(left, TestMachines.purification());
        w.placeMachine(right, TestMachines.purification());
        for (int k = 1; k <= 3; k++) {
            w.placePipe(TestWorld.at(k, 0, 0));
            w.placePipe(TestWorld.at(-k, 0, 0));
        }
        w.machine(source).energy().set(100_000);
        w.machine(source).inTank().set(Chemical.WATER, 100);
        w.tick(300);
        long a = w.machine(left).inTank().amount();
        long b = w.machine(right).inTank().amount();
        assertTrue(a > 10 && b > 10, "both got oxygen: " + a + " and " + b);
        assertTrue(Math.abs(a - b) <= 2, "evenly: " + a + " and " + b);
    }

    @Test
    void aStorageTankTakesWhatItsFilterAllowsAndGivesItBack() {
        MachineType tank = new MachineType("chemical_tank", List::of, 1, 1, 64_000, 0, c -> true);
        MachineType water = new MachineType("fluid_tank", List::of, 1, 1, 32_000, 0, Chemical.WATER::equals);
        Machine any = new Machine(tank);
        Machine onlyWater = new Machine(water);
        assertEquals(500, any.inTank().fill(Chemical.HYDROGEN, 500, false));
        assertEquals(0, onlyWater.inTank().fill(Chemical.HYDROGEN, 500, false));
        assertEquals(500, onlyWater.inTank().fill(Chemical.WATER, 500, false));
        assertEquals(1, any.ports().outputs().size());
        assertTrue(any.ports().outputs().getFirst() == any.ports().inputs().getFirst(), "the same tank in and out");
    }

    @Test
    void facesTankContentsAndUpgradesSurviveSavingAndPickingUp() {
        Machine m = new Machine(infuser());
        m.setFace(0, Side.IN1);
        m.setFace(4, Side.OUT2);
        m.inTank().set(Chemical.HYDROGEN, 30);
        m.inTank2().set(Chemical.OXYGEN, 20);
        for (String text : List.of(m.save(), m.saveSettings())) {
            Machine copy = new Machine(infuser());
            copy.load(text);
            assertEquals(Side.IN1, copy.face(0));
            assertEquals(Side.ALL, copy.face(1));
            assertEquals(Side.OUT2, copy.face(4));
            assertEquals(30, copy.inTank().amount());
            assertEquals(20, copy.inTank2().amount());
        }
    }

    @Test
    void twoGasesMakeAThirdAndSecondTankIsConsumed() {
        Machine m = new Machine(infuser());
        m.energy().set(1_000);
        m.inTank().set(Chemical.HYDROGEN, 25);
        m.inTank2().set(Chemical.OXYGEN, 15);
        for (int i = 0; i < 100; i++) {
            m.tick();
        }
        assertEquals(10, m.outTank().amount(), "one operation: 10 of each in, 10 of x out");
        assertEquals(X, m.outTank().type());
        assertEquals(15, m.inTank().amount());
        assertEquals(5, m.inTank2().amount());
    }
}
