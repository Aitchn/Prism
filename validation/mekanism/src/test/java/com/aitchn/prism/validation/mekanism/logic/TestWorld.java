package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A tiny in-memory world for chain tests. It drives the real {@link Plant} (networks) and the real {@link Machine}
 * (tick, save, load); only the Bukkit parts (blocks, chunks) are replaced by maps. Every tick it checks that the
 * cable step neither creates nor destroys energy.
 *
 * <p>"Unloading" mirrors the server adapter: the machine is saved as text, dropped from the plant and rebuilt from
 * that text when its chunk loads again.
 */
public final class TestWorld {

    public static final String W = "w";

    public final Plant plant = new Plant();
    private final Map<Pos, MachineType> types = new HashMap<>();
    private final Map<Pos, String> stored = new HashMap<>();
    public long solarEnergyMade;
    public long ticks;

    public static Pos at(int x, int y, int z) {
        return new Pos(W, x, y, z);
    }

    public Machine machine(Pos pos) {
        return plant.machines().get(pos);
    }

    public SolarGenerator solar(Pos pos) {
        return plant.solars().get(pos);
    }

    public void placeMachine(Pos pos, MachineType type) {
        types.put(pos, type);
        plant.machines().put(pos, new Machine(type));
        plant.markDirty();
    }

    public void placeSolar(Pos pos) {
        plant.solars().put(pos, new SolarGenerator());
        plant.markDirty();
    }

    public void placeCable(Pos pos) {
        plant.cables().add(pos);
        plant.markDirty();
    }

    public void placePipe(Pos pos) {
        plant.pipes().add(pos);
        plant.markDirty();
    }

    /** Breaking a cable or pipe. */
    public void breakConduit(Pos pos) {
        plant.cables().remove(pos);
        plant.pipes().remove(pos);
        plant.markDirty();
    }

    /** Breaking a machine: returns what a player would get back (input and output slots). */
    public Optional<Stack> breakMachine(Pos pos) {
        Machine m = plant.machines().remove(pos);
        types.remove(pos);
        stored.remove(pos);
        plant.markDirty();
        if (m == null) {
            return Optional.empty();
        }
        m.markDead();
        return Optional.ofNullable(m.output());
    }

    /** One game tick: generators, cable and pipe transfer (energy conserved), then every machine. */
    public void tick() {
        ticks++;
        long energyAtStart = totalEnergy();
        long madeBefore = solarEnergyMade;
        for (SolarGenerator s : plant.solars().values()) {
            long before = s.energy().stored();
            s.tick(true);
            solarEnergyMade += s.energy().stored() - before;
        }
        long before = totalEnergy();
        plant.tickNetworks();
        assertEquals(before, totalEnergy(), "the network step must not create or destroy energy (tick " + ticks + ")");
        for (Machine m : plant.machines().values()) {
            m.tick();
        }
        assertTrue(totalEnergy() <= energyAtStart + (solarEnergyMade - madeBefore),
                "energy appeared from nowhere (tick " + ticks + ")");
    }

    public void tick(int n) {
        for (int i = 0; i < n; i++) {
            tick();
        }
    }

    public long totalEnergy() {
        long sum = 0;
        for (Machine m : plant.machines().values()) {
            sum += m.energy().stored();
        }
        for (SolarGenerator s : plant.solars().values()) {
            sum += s.energy().stored();
        }
        return sum;
    }

    /** Saves every machine, drops it from memory and loads it again, like a chunk unload followed by a load. */
    public void reloadAll() {
        for (Pos pos : types.keySet().toArray(new Pos[0])) {
            Machine old = plant.machines().get(pos);
            stored.put(pos, old.save());
            Machine fresh = new Machine(types.get(pos));
            fresh.load(stored.get(pos));
            plant.machines().put(pos, fresh);
        }
        plant.markDirty();
    }

    /** The player moves the whole output slot of one machine into the input slot of another; returns items moved. */
    public int shuttle(Pos from, Pos to) {
        Machine source = machine(from);
        Machine target = machine(to);
        if (source.output() == null) {
            return 0;
        }
        Stack out = source.output();
        Stack left = target.insertInput(out);
        int moved = out.count() - left.count();
        source.setOutput(left.isEmpty() ? null : left);
        return moved;
    }
}
