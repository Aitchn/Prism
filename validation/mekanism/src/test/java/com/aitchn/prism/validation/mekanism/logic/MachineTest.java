package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MachineTest {

    private static void run(Machine m, int ticks) {
        for (int i = 0; i < ticks; i++) {
            m.tick();
        }
    }

    @Test
    void enrichmentTurnsOneOreIntoTwoDustAndUsesEnergy() {
        Machine m = new Machine(TestMachines.enrichment());
        m.energy().set(20_000);
        assertEquals(0, m.insertInput(new Stack("mekanism:osmium_ore", 1)).count());
        run(m, 199);
        assertTrue(m.takeOutput().isEmpty(), "not finished after 199 ticks");
        run(m, 1);
        assertEquals(new Stack("mekanism:osmium_dust", 2), m.takeOutput().orElseThrow());
        assertEquals(20_000 - 200 * TestMachines.TICK_ENERGY, m.energy().stored());
    }

    @Test
    void withoutEnergyNothingHappens() {
        Machine m = new Machine(TestMachines.crusher());
        m.insertInput(new Stack("minecraft:cobblestone", 3));
        run(m, 500);
        assertFalse(m.running());
        assertEquals(3, m.input().count());
    }

    @Test
    void rejectsItemsWithNoRecipe() {
        Machine m = new Machine(TestMachines.crusher());
        assertEquals(5, m.insertInput(new Stack("minecraft:dirt", 5)).count());
    }

    @Test
    void purificationNeedsOxygenAndConsumesIt() {
        Machine m = new Machine(TestMachines.purification());
        m.energy().receive(2_000, false);
        m.insertInput(new Stack("mekanism:osmium_ore", 2));
        run(m, 50);
        assertFalse(m.running(), "no oxygen yet");
        m.inTank().fill(Chemical.OXYGEN, 1, false);
        for (int i = 0; i < 300; i++) {
            m.energy().receive(50, false);
            m.tick();
        }
        assertEquals(new Stack("mekanism:osmium_clump", 3), m.takeOutput().orElseThrow());
        assertEquals(0, m.inTank().amount());
        assertEquals(1, m.input().count());
    }

    @Test
    void inputTankRefusesChemicalsNoRecipeUses() {
        Machine m = new Machine(TestMachines.purification());
        assertEquals(0, m.inTank().fill(Chemical.HYDROGEN, 100, false), "only oxygen is used here");
        assertEquals(0, m.inTank().fill(Chemical.WATER, 100, false));
        assertEquals(100, m.inTank().fill(Chemical.OXYGEN, 100, false));
    }

    @Test
    void separatorSplitsWaterIntoTwoTanks() {
        Machine m = new Machine(TestMachines.separator());
        m.energy().receive(1_000, false);
        m.inTank().fill(Chemical.WATER, 4, false);
        run(m, 1);
        assertEquals(Chemical.HYDROGEN, m.outTank().type());
        assertEquals(2, m.outTank().amount());
        assertEquals(Chemical.OXYGEN, m.outTank2().type());
        assertEquals(1, m.outTank2().amount());
    }

    @Test
    void fullOutputBlocksTheNextOperation() {
        Machine m = new Machine(TestMachines.enrichment());
        m.insertInput(new Stack("minecraft:raw_iron", 64));
        m.energy().set(20_000);
        for (int i = 0; i < 200 * 40; i++) {
            m.energy().produce(50);
            m.tick();
        }
        assertEquals(64, m.output().count());
        assertTrue(m.input().count() > 0, "input left because the output slot is full");
    }

    @Test
    void idleTicksCountUpWhenStarvedAndResetWhenWorking() {
        Machine m = new Machine(TestMachines.enrichment());
        m.insertInput(new Stack("mekanism:osmium_ore", 1));
        run(m, 5);
        assertEquals(Machine.IDLE_AFTER, m.idleTicks(), "starved: stays idle");
        m.energy().set(20_000);
        m.tick();
        assertEquals(0, m.idleTicks());
        assertEquals(Machine.IDLE_AFTER, new Machine(TestMachines.crusher()).idleTicks(), "a new machine starts idle");
    }

    @Test
    void stateSurvivesSaveAndLoadIncludingARunningOperation() {
        Machine a = new Machine(TestMachines.purification());
        a.energy().set(12_345);
        a.insertInput(new Stack("mekanism:osmium_ore", 3));
        a.inTank().fill(Chemical.OXYGEN, 5, false);
        a.outTank().set(Chemical.HYDROGEN, 77);
        run(a, 100);
        assertTrue(a.running());
        String saved = a.save();

        Machine b = new Machine(TestMachines.purification());
        b.load(saved);
        assertEquals(a.energy().stored(), b.energy().stored());
        assertEquals(a.input(), b.input());
        assertEquals(a.inTank().amount(), b.inTank().amount());
        assertEquals(Chemical.HYDROGEN, b.outTank().type());
        assertEquals(77, b.outTank().amount());
        assertTrue(b.running());
        assertEquals(a.progress(), b.progress());
        assertEquals(saved, b.save());
        // it finishes where the original would
        b.energy().set(20_000);
        run(b, 200);
        assertEquals(new Stack("mekanism:osmium_clump", 3), b.output());
    }

    @Test
    void damagedStateIsSkippedNotFatal() {
        Machine m = new Machine(TestMachines.crusher());
        m.load("e=abc|i=broken|o=mekanism:osmium_dust,2|x=1|garbage|r=99|p=5");
        assertEquals(0, m.energy().stored());
        assertNull(m.input());
        assertEquals(new Stack("mekanism:osmium_dust", 2), m.output());
        assertFalse(m.running());
    }

    @Test
    void emptyMachineSavesJustItsEnergy() {
        assertEquals("e=0", new Machine(TestMachines.crusher()).save());
    }
}
