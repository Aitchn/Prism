package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** Several machines, cables and pipes working together, with unloads in the middle and broken parts. */
class ChainTest {

    private static final Pos SOLAR = TestWorld.at(0, 0, 0);
    private static final Pos SEPARATOR = TestWorld.at(2, 0, 0);
    private static final Pos PURIFIER = TestWorld.at(2, 0, 3);
    private static final Pos ENRICHER = TestWorld.at(4, 0, 0);

    /**
     * Solar -> cables -> separator and purifier; water in the separator; pipe separator -> purifier; osmium ore
     * hand-fed to the purifier. Returns the world after {@code ticks}, optionally unloading everything every 37 ticks.
     */
    private static TestWorld factory(int ticks, boolean reloadEvery37) {
        TestWorld w = new TestWorld();
        w.placeSolar(SOLAR);
        w.placeMachine(SEPARATOR, TestMachines.separator());
        w.placeMachine(PURIFIER, TestMachines.purification());
        w.placeCable(TestWorld.at(1, 0, 0));
        w.placeCable(TestWorld.at(1, 0, 1));
        w.placeCable(TestWorld.at(1, 0, 2));
        w.placeCable(TestWorld.at(1, 0, 3));
        // pipe from the separator's side up to the purifier: (2,0,1),(2,0,2)
        w.placePipe(TestWorld.at(2, 0, 1));
        w.placePipe(TestWorld.at(2, 0, 2));
        w.machine(SEPARATOR).inTank().set(Chemical.WATER, 10_000);
        w.machine(PURIFIER).insertInput(new Stack("mekanism:osmium_ore", 8));
        for (int i = 1; i <= ticks; i++) {
            w.tick();
            if (reloadEvery37 && i % 37 == 0) {
                w.reloadAll();
            }
        }
        return w;
    }

    @Test
    void oreBecomesClumpsThroughSolarCablesPipesAndTwoMachines() {
        TestWorld w = factory(12_000, false);
        Machine purifier = w.machine(PURIFIER);
        assertEquals(24, purifier.output().count() + (purifier.input() == null ? 0 : purifier.input().count() * 3),
                "8 ore -> 24 clumps, nothing lost");
        assertEquals("mekanism:osmium_clump", purifier.output().id());
        assertNull(purifier.input(), "all ore used");
    }

    @Test
    void theSameRunWithUnloadsInTheMiddleEndsTheSame() {
        TestWorld plain = factory(12_000, false);
        TestWorld reloaded = factory(12_000, true);
        assertEquals(plain.machine(PURIFIER).output(), reloaded.machine(PURIFIER).output());
        assertEquals(plain.machine(SEPARATOR).inTank().amount(), reloaded.machine(SEPARATOR).inTank().amount());
        assertEquals(plain.totalEnergy(), reloaded.totalEnergy(), "no joule gained or lost by saving and loading");
    }

    @Test
    void breakingTheMiddleCableStopsTheFarMachineAndReplacingResumesIt() {
        TestWorld w = new TestWorld();
        w.placeSolar(SOLAR);
        w.placeCable(TestWorld.at(1, 0, 0));
        w.placeCable(TestWorld.at(2, 0, 0));
        w.placeCable(TestWorld.at(3, 0, 0));
        w.placeMachine(TestWorld.at(4, 0, 0), TestMachines.crusher());
        Machine crusher = w.machine(TestWorld.at(4, 0, 0));
        crusher.insertInput(new Stack("minecraft:cobblestone", 64));
        w.tick(300);
        assertNotNull(crusher.output(), "powered through the cable");
        long made = crusher.output().count();
        w.breakConduit(TestWorld.at(2, 0, 0));
        long energyAtBreak = crusher.energy().stored();
        w.tick(2_000);
        assertTrue(crusher.output().count() <= made + 1, "no more than the energy it already had");
        assertTrue(crusher.energy().stored() <= energyAtBreak, "no energy arrives across a gap");
        w.placeCable(TestWorld.at(2, 0, 0));
        long before = crusher.output().count();
        w.tick(1_000);
        assertTrue(crusher.output().count() > before, "works again once the cable is back");
    }

    @Test
    void twoMachinesOnOneCableBothGetEnergy() {
        TestWorld w = new TestWorld();
        w.placeSolar(SOLAR);
        w.placeCable(TestWorld.at(1, 0, 0));
        w.placeMachine(TestWorld.at(1, 1, 0), TestMachines.crusher());
        w.placeMachine(TestWorld.at(1, -1, 0), TestMachines.enrichment());
        w.machine(TestWorld.at(1, 1, 0)).insertInput(new Stack("minecraft:cobblestone", 20));
        w.machine(TestWorld.at(1, -1, 0)).insertInput(new Stack("minecraft:raw_iron", 20));
        w.tick(6_000);
        assertTrue(w.machine(TestWorld.at(1, 1, 0)).output().count() >= 5, "crusher made progress");
        assertTrue(w.machine(TestWorld.at(1, -1, 0)).output().count() >= 10, "enrichment made progress");
    }

    @Test
    void aFullOutputSlotStallsTheMachineAndEmptyingItResumes() {
        TestWorld w = new TestWorld();
        w.placeMachine(ENRICHER, TestMachines.enrichment());
        Machine m = w.machine(ENRICHER);
        m.energy().set(20_000);
        m.setOutput(new Stack("mekanism:osmium_dust", 64));
        m.insertInput(new Stack("mekanism:osmium_ore", 1));
        w.tick(500);
        assertEquals(64, m.output().count(), "blocked: nothing added past a full stack");
        assertEquals(1, m.input().count(), "input not consumed while blocked");
        m.takeOutput();
        w.tick(300);
        assertEquals(2, m.output().count());
    }

    @Test
    void handCarryingChainEnrichmentToCrusherKeepsEveryItem() {
        TestWorld w = new TestWorld();
        Pos a = TestWorld.at(0, 0, 0);
        Pos b = TestWorld.at(5, 0, 0);
        w.placeMachine(a, TestMachines.enrichment());
        w.placeMachine(b, TestMachines.crusher());
        w.machine(a).energy().set(20_000);
        w.machine(b).energy().set(20_000);
        w.machine(a).insertInput(new Stack("mekanism:osmium_ore", 2));
        int carried = 0;
        for (int i = 0; i < 3_000; i++) {
            w.tick();
            if (i % 50 == 0) {
                carried += w.shuttle(a, b); // dust goes into the crusher; it has no recipe for dust, so it stays put
            }
        }
        assertEquals(0, carried, "the crusher rejects dust, the enrichment output must not vanish");
        assertEquals(4, w.machine(a).output().count());
    }

    @Test
    void randomPlacingAndBreakingKeepsTheGridConsistent() {
        Random random = new Random(42);
        TestWorld w = new TestWorld();
        for (int step = 0; step < 3_000; step++) {
            Pos pos = TestWorld.at(random.nextInt(6), 0, random.nextInt(6));
            switch (random.nextInt(8)) {
                case 0 -> w.placeCable(pos);
                case 1 -> w.placePipe(pos);
                case 2 -> {
                    if (isFree(w, pos)) {
                        w.placeMachine(pos, random.nextBoolean() ? TestMachines.crusher() : TestMachines.separator());
                    }
                }
                case 3 -> {
                    if (isFree(w, pos)) {
                        w.placeSolar(pos);
                    }
                }
                case 4 -> w.breakConduit(pos);
                case 5 -> w.breakMachine(pos);
                case 6 -> w.reloadAll();
                default -> w.tick(3);
            }
            if (w.plant.machines().containsKey(pos) && w.machine(pos).type().id().equals("crusher")
                    && random.nextInt(4) == 0) {
                w.machine(pos).insertInput(new Stack("minecraft:cobblestone", 3));
            }
        }
        for (Pos pos : w.plant.machines().keySet()) {
            assertTrue(!w.plant.solars().containsKey(pos) && !w.plant.cables().contains(pos)
                    && !w.plant.pipes().contains(pos), "one block per position: " + pos);
        }
        for (var net : w.plant.cableNets()) {
            for (Pos p : net.conduits()) {
                assertTrue(w.plant.cables().contains(p), "network lists a removed cable " + p);
            }
        }
    }

    private static boolean isFree(TestWorld w, Pos pos) {
        return !w.plant.machines().containsKey(pos) && !w.plant.solars().containsKey(pos)
                && !w.plant.cables().contains(pos) && !w.plant.pipes().contains(pos);
    }
}
