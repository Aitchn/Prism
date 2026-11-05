package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        Machine m = new Machine(Catalog.enrichmentChamber());
        m.energy().set(20_000);
        assertEquals(0, m.insertInput(new Stack("mekanism:osmium_ore", 1)).count());
        run(m, 199);
        assertTrue(m.takeOutput().isEmpty(), "not finished after 199 ticks");
        run(m, 1);
        assertEquals(new Stack("mekanism:osmium_dust", 2), m.takeOutput().orElseThrow());
        assertEquals(20_000 - 200 * Catalog.TICK_ENERGY, m.energy().stored());
    }

    @Test
    void withoutEnergyNothingHappens() {
        Machine m = new Machine(Catalog.crusher());
        m.insertInput(new Stack("minecraft:cobblestone", 3));
        run(m, 500);
        assertFalse(m.running());
        assertEquals(3, m.input().count());
    }

    @Test
    void rejectsItemsWithNoRecipe() {
        Machine m = new Machine(Catalog.crusher());
        assertEquals(5, m.insertInput(new Stack("minecraft:dirt", 5)).count());
    }

    @Test
    void purificationNeedsOxygenAndConsumesIt() {
        Machine m = new Machine(Catalog.purificationChamber());
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
    void separatorSplitsWaterIntoTwoTanks() {
        Machine m = new Machine(Catalog.electrolyticSeparator());
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
        Machine m = new Machine(Catalog.enrichmentChamber());
        m.insertInput(new Stack("minecraft:iron_ore", 64));
        m.energy().set(20_000);
        for (int i = 0; i < 200 * 40; i++) {
            m.energy().produce(50);
            m.tick();
        }
        assertEquals(64, m.output().count());
        assertTrue(m.input().count() > 0, "input left because the output slot is full");
    }
}
