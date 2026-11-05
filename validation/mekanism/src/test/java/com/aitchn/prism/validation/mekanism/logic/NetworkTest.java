package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NetworkTest {

    private static Pos p(int x) {
        return new Pos("w", x, 64, 0);
    }

    @Test
    void cablesSplitIntoComponentsWhenOneIsRemoved() {
        Set<Pos> cables = new HashSet<>();
        for (int x = 0; x < 5; x++) {
            cables.add(p(x));
        }
        assertEquals(1, Networks.components(cables).size());
        cables.remove(p(2));
        assertEquals(2, Networks.components(cables).size());
    }

    @Test
    void solarEnergyReachesBothMachinesEvenly() {
        SolarGenerator solar = new SolarGenerator();
        Machine a = new Machine(TestMachines.crusher());
        Machine b = new Machine(TestMachines.crusher());
        Map<Pos, EnergyBuffer> sources = Map.of(p(0), solar.energy());
        Map<Pos, EnergyBuffer> sinks = new HashMap<>(Map.of(p(4), a.energy()));
        Set<Pos> cables = Set.of(p(1), p(2), p(3));
        // layout: solar(0) cable(1,2,3) a(4); b sits above the last cable
        sinks.put(p(3).offset(0, 1, 0), b.energy());
        for (int i = 0; i < 100; i++) {
            solar.tick(true);
        }
        long moved = Networks.distributeEnergy(cables, Networks.lookup(sources), Networks.lookup(sinks), 1_000);
        assertEquals(1_000, moved);
        assertEquals(500, a.energy().stored());
        assertEquals(500, b.energy().stored());
        assertEquals(4_000, solar.energy().stored());
    }

    @Test
    void separateNetworksDoNotShareEnergy() {
        SolarGenerator solar = new SolarGenerator();
        Machine far = new Machine(TestMachines.crusher());
        solar.tick(true);
        Set<Pos> cables = Set.of(p(1), p(10));
        Map<Pos, EnergyBuffer> sources = Map.of(p(0), solar.energy());
        Map<Pos, EnergyBuffer> sinks = Map.of(p(11), far.energy());
        Networks.distributeEnergy(cables, Networks.lookup(sources), Networks.lookup(sinks), 1_000);
        assertEquals(0, far.energy().stored());
    }

    @Test
    void pipesMoveChemicalFromTankToMachine() {
        ChemicalTank source = new ChemicalTank(1_000);
        source.fill(Chemical.OXYGEN, 500, false);
        Machine m = new Machine(TestMachines.purification());
        Set<Pos> pipes = Set.of(p(1), p(2));
        long moved = Networks.distributeChemical(pipes, Networks.lookup(Map.of(p(0), List.of(source))),
                Networks.lookup(Map.of(p(3), List.of(m.inTank()))), 100);
        assertTrue(moved > 0);
        assertEquals(moved, m.inTank().amount());
        assertEquals(500 - moved, source.amount());
    }

    @Test
    void separatorGasReachesThePurifierThroughAPipeButHydrogenIsRefused() {
        Machine separator = new Machine(TestMachines.separator());
        separator.outTank().set(Chemical.HYDROGEN, 50);
        separator.outTank2().set(Chemical.OXYGEN, 50);
        Machine purifier = new Machine(TestMachines.purification());
        Set<Pos> pipes = Set.of(p(1), p(2));
        Map<Pos, Machine> machines = Map.of(p(0), separator, p(3), purifier);
        long moved = Networks.distributeChemical(pipes, pos -> machines.get(pos) == null ? null
                : machines.get(pos).ports().outputs(), pos -> machines.get(pos) == null ? null
                : machines.get(pos).ports().inputs(), 100);
        assertEquals(50, moved, "only the oxygen moved");
        assertEquals(50, purifier.inTank().amount());
        assertEquals(Chemical.OXYGEN, purifier.inTank().type());
        assertEquals(50, separator.outTank().amount(), "hydrogen stayed");
    }

    @Test
    void networksAreBuiltOnceAndFindTheirEndpoints() {
        Set<Pos> cables = Set.of(p(1), p(2));
        var nets = Networks.build(cables);
        assertEquals(1, nets.size());
        assertEquals(Set.of(p(0), p(3), p(1).offset(0, 1, 0), p(1).offset(0, -1, 0), p(1).offset(0, 0, 1),
                p(1).offset(0, 0, -1), p(2).offset(0, 1, 0), p(2).offset(0, -1, 0), p(2).offset(0, 0, 1),
                p(2).offset(0, 0, -1)), nets.getFirst().endpoints());
        assertEquals(nets.getFirst(), Networks.containing(nets, p(2)));
        assertEquals(null, Networks.containing(nets, p(9)));
    }

    @Test
    void tankHoldsOnlyOneChemical() {
        ChemicalTank t = new ChemicalTank(100);
        assertEquals(10, t.fill(Chemical.OXYGEN, 10, false));
        assertEquals(0, t.fill(Chemical.HYDROGEN, 10, false));
        assertFalse(t.drain(null, 10, false) == 0);
        assertEquals(10, t.fill(Chemical.HYDROGEN, 10, false));
    }
}
