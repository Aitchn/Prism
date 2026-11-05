package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Whole production lines, as scenarios shared by the content test (real YAML recipes) and by the quick local runner.
 * A {@link Factory} places machines on a grid (four blocks apart), joins neighbours with pipe segments whose faces are
 * set to the tanks they should use, wires every machine to a charged battery with cables, and carries items by hand
 * from machine to machine like a player would. Every scenario can be repeated with all machines saved and loaded again
 * every few ticks; the result must not change.
 */
public final class Chains {

    private Chains() {}

    /** Where machine types come from: the loaded content in the real test. */
    public interface Types {
        MachineType machine(String id);

        MachineType battery();
    }

    /** A grid factory. */
    public static final class Factory {
        public final TestWorld world = new TestWorld();
        private final Types types;
        private final List<Pos> machines = new ArrayList<>();
        private final List<Pos> carry = new ArrayList<>();
        private Pos battery;
        private long startEnergy;

        public Factory(Types types) {
            this.types = types;
        }

        /** A machine at grid cell (gx, gz); cells are four blocks apart so pipes fit between neighbours. */
        public Pos machine(String id, int gx, int gz) {
            Pos pos = TestWorld.at(gx * 4, 0, gz * 4);
            world.placeMachine(pos, types.machine(id));
            machines.add(pos);
            return pos;
        }

        public Machine at(Pos pos) {
            return world.machine(pos);
        }

        /**
         * Joins two machines on the same grid row or column with three pipes. {@code out} is the mode of the face
         * of {@code from} that touches the pipe, {@code in} the mode of the face of {@code to}.
         */
        public void link(Pos from, Side out, Pos to, Side in) {
            int dx = Integer.signum(to.x() - from.x());
            int dz = Integer.signum(to.z() - from.z());
            assertTrue((dx == 0) != (dz == 0), "machines must share a row or a column");
            int face = dx > 0 ? 0 : dx < 0 ? 1 : dz > 0 ? 4 : 5;
            for (int k = 1; k < 4; k++) {
                world.placePipe(TestWorld.at(from.x() + dx * k, 0, from.z() + dz * k));
            }
            at(from).setFace(face, out);
            at(to).setFace(face ^ 1, in);
        }

        /** The order a player carries items in: each machine's output goes to the next machine's input. */
        public void carry(Pos... path) {
            carry.clear();
            carry.addAll(List.of(path));
        }

        /** Places the battery and wires every machine to it. Call after all machines are placed. */
        public void wire() {
            battery = TestWorld.at(-4, 0, 0);
            world.placeMachine(battery, types.battery());
            at(battery).energy().set(1_500_000);
            List<Pos> all = new ArrayList<>(machines);
            all.add(battery);
            for (Pos pos : all) {
                int x = pos.x();
                int z = pos.z();
                world.placeCable(TestWorld.at(x, 1, z));
                while (x != battery.x()) {
                    x += Integer.signum(battery.x() - x);
                    world.placeCable(TestWorld.at(x, 1, z));
                }
                while (z != battery.z()) {
                    z += Integer.signum(battery.z() - z);
                    world.placeCable(TestWorld.at(x, 1, z));
                }
            }
            startEnergy = world.totalEnergy();
        }

        /** Runs until {@code done} holds or the tick limit is hit. */
        public void run(int limit, int reloadEvery, BooleanSupplier done) {
            for (int tick = 1; tick <= limit && !done.getAsBoolean(); tick++) {
                world.tick();
                if (tick % 7 == 0) {
                    for (int i = 0; i + 1 < carry.size(); i++) {
                        world.shuttle(carry.get(i), carry.get(i + 1));
                    }
                }
                if (reloadEvery > 0 && tick % reloadEvery == 0) {
                    world.reloadAll();
                }
            }
        }

        /** What every machine holds, for assertion messages. */
        public String describe() {
            StringBuilder out = new StringBuilder("\nafter " + world.ticks + " ticks:");
            for (Pos pos : machines) {
                Machine m = at(pos);
                out.append("\n  ").append(m.type().id()).append(' ').append(pos).append(" in=").append(m.input())
                        .append(" extra=").append(m.extra()).append(" out=").append(m.output()).append(" running=")
                        .append(m.running()).append(" energy=").append(m.energy().stored()).append(" tanks=[")
                        .append(tank(m.inTank())).append(", ").append(tank(m.inTank2())).append(" -> ")
                        .append(tank(m.outTank())).append(", ").append(tank(m.outTank2())).append(']');
            }
            return out.toString();
        }

        private static String tank(ChemicalTank t) {
            return t.type() == null ? "-" : t.type().id() + ":" + t.amount();
        }

        public int output(Pos pos, String item) {
            Stack out = at(pos).output();
            return out != null && out.id().equals(item) ? out.count() : 0;
        }

        /** Nothing may be stuck in the machines that items pass through, and no energy may have appeared. */
        public void assertClean(Pos last) {
            for (Pos pos : carry) {
                Machine m = at(pos);
                assertNull(m.input(), pos + " still holds input " + m.input());
                assertNull(m.extra(), pos + " still holds a second input " + m.extra());
                assertTrue(!m.running(), pos + " is still running");
                if (!pos.equals(last)) {
                    assertNull(m.output(), pos + " still holds output " + m.output());
                }
            }
            assertTrue(world.totalEnergy() <= startEnergy, "energy cannot grow without a generator");
        }
    }

    private static Stack s(String id, int count) {
        return new Stack(id, count);
    }

    // ------------------------------------------------------------------------------------------------ scenarios

    /** 4 raw iron -> 12 clumps -> dirty dust -> dust -> 12 iron ingots; oxygen from a separator over one pipe. */
    public static void ironThreeTimes(Types types, int reloadEvery) {
        Factory f = new Factory(types);
        Pos purifier = f.machine("purification_chamber", 0, 0);
        Pos crusher = f.machine("crusher", 1, 0);
        Pos enricher = f.machine("enrichment_chamber", 2, 0);
        Pos smelter = f.machine("energized_smelter", 3, 0);
        Pos separator = f.machine("electrolytic_separator", 0, 1);
        f.link(separator, Side.OUT2, purifier, Side.ALL);
        f.wire();
        f.carry(purifier, crusher, enricher, smelter);
        f.at(separator).inTank().set(Chemical.WATER, 24); // a separator runs while it has water: just enough
        f.at(purifier).insertInput(s("minecraft:raw_iron", 4));
        f.run(60_000, reloadEvery, () -> f.output(smelter, "minecraft:iron_ingot") >= 12);
        assertEquals(12, f.output(smelter, "minecraft:iron_ingot"), f.describe());
        f.assertClean(smelter);
    }

    /** 2 osmium ore -> 8 shards (hydrogen chloride made on site) -> 8 clumps -> ... -> 8 ingots. */
    public static void osmiumFourTimes(Types types, int reloadEvery) {
        Factory f = new Factory(types);
        Pos injector = f.machine("chemical_injection_chamber", 0, 2);
        Pos purifier = f.machine("purification_chamber", 1, 2);
        Pos crusher = f.machine("crusher", 2, 2);
        Pos enricher = f.machine("enrichment_chamber", 3, 2);
        Pos smelter = f.machine("energized_smelter", 4, 2);
        Pos separator = f.machine("electrolytic_separator", 1, 1);
        Pos infuser = f.machine("chemical_infuser", 0, 1);
        Pos oxidizer = f.machine("chemical_oxidizer", -1, 1);
        f.link(separator, Side.OUT1, infuser, Side.IN1); // hydrogen into the left tank
        f.link(oxidizer, Side.OUT1, infuser, Side.IN2); // chlorine into the right tank
        f.link(infuser, Side.OUT1, injector, Side.ALL); // hydrogen chloride to the injection chamber
        f.link(separator, Side.OUT2, purifier, Side.ALL); // oxygen to the purification chamber
        f.wire();
        f.carry(injector, purifier, crusher, enricher, smelter);
        f.at(separator).inTank().set(Chemical.WATER, 100);
        f.at(oxidizer).insertInput(s("mekanism:salt", 1));
        f.at(injector).insertInput(s("mekanism:osmium_ore", 2));
        f.run(80_000, reloadEvery, () -> f.output(smelter, "mekanism:osmium_ingot") >= 8);
        assertEquals(8, f.output(smelter, "mekanism:osmium_ingot"), f.describe());
        f.assertClean(smelter);
    }

    /**
     * 1 osmium ore -> 5 ingots through the whole chemical chain: sulfur -> sulfur dioxide -> trioxide -> sulfuric acid;
     * acid dissolves the ore into slurry, the washer cleans it, the crystallizer makes five crystals, hydrogen chloride
     * turns them into shards, then oxygen, crusher, enrichment and the smelter.
     */
    public static void osmiumFiveTimes(Types types, int reloadEvery) {
        Factory f = new Factory(types);
        Pos sulfur = f.machine("chemical_oxidizer", 0, 0);
        Pos trioxide = f.machine("chemical_infuser", 1, 0);
        Pos acid = f.machine("chemical_infuser", 2, 0);
        Pos dissolver = f.machine("chemical_dissolution_chamber", 3, 0);
        Pos washer = f.machine("chemical_washer", 4, 0);
        Pos crystallizer = f.machine("chemical_crystallizer", 5, 0);
        Pos separator = f.machine("electrolytic_separator", 1, 1);
        Pos hcl = f.machine("chemical_infuser", 2, 1);
        Pos salt = f.machine("chemical_oxidizer", 3, 1);
        Pos injector = f.machine("chemical_injection_chamber", 2, 2);
        Pos purifier = f.machine("purification_chamber", 1, 2);
        Pos crusher = f.machine("crusher", 3, 2);
        Pos enricher = f.machine("enrichment_chamber", 4, 2);
        Pos smelter = f.machine("energized_smelter", 5, 2);
        f.link(sulfur, Side.OUT1, trioxide, Side.IN1); // sulfur dioxide
        f.link(separator, Side.OUT2, trioxide, Side.IN2); // oxygen for the trioxide step
        f.link(trioxide, Side.OUT1, acid, Side.IN1); // sulfur trioxide
        f.link(acid, Side.OUT1, dissolver, Side.ALL); // sulfuric acid
        f.link(dissolver, Side.OUT1, washer, Side.IN1); // dirty slurry
        f.link(washer, Side.OUT1, crystallizer, Side.ALL); // clean slurry
        f.link(separator, Side.OUT1, hcl, Side.IN1); // hydrogen
        f.link(salt, Side.OUT1, hcl, Side.IN2); // chlorine
        f.link(hcl, Side.OUT1, injector, Side.ALL); // hydrogen chloride
        f.link(separator, Side.OUT2, purifier, Side.ALL); // oxygen for the shards
        f.wire();
        f.carry(crystallizer, injector, purifier, crusher, enricher, smelter);
        f.at(separator).inTank().set(Chemical.WATER, 400);
        f.at(acid).inTank2().set(Chemical.WATER, 100); // water comes from a bucket
        f.at(washer).inTank2().set(Chemical.WATER, 100);
        f.at(sulfur).insertInput(s("mekanism:sulfur_dust", 1));
        f.at(salt).insertInput(s("mekanism:salt", 1));
        f.at(dissolver).insertInput(s("mekanism:osmium_ore", 1));
        f.run(120_000, reloadEvery, () -> f.output(smelter, "mekanism:osmium_ingot") >= 5);
        assertEquals(5, f.output(smelter, "mekanism:osmium_ingot"), f.describe());
        f.assertClean(smelter);
        assertNull(f.at(dissolver).input());
    }

    /** Iron ingots and coal become steel through two metallurgic infusers and a smelter. */
    public static void steel(Types types, int reloadEvery) {
        Factory f = new Factory(types);
        Pos first = f.machine("metallurgic_infuser", 0, 0);
        Pos second = f.machine("metallurgic_infuser", 1, 0);
        Pos smelter = f.machine("energized_smelter", 2, 0);
        f.wire();
        f.carry(first, second, smelter);
        f.at(first).insertInput(s("minecraft:iron_ingot", 3));
        f.at(first).insertExtra(s("minecraft:coal", 3));
        f.at(second).insertExtra(s("minecraft:coal", 3));
        f.run(60_000, reloadEvery, () -> f.output(smelter, "mekanism:steel_ingot") >= 3);
        assertEquals(3, f.output(smelter, "mekanism:steel_ingot"), f.describe());
        f.assertClean(smelter);
    }

    /** Upgraded machines finish the same job much sooner. */
    public static void upgrades(Types types) {
        long[] ticks = new long[2];
        for (int variant = 0; variant < 2; variant++) {
            Factory f = new Factory(types);
            Pos crusher = f.machine("crusher", 0, 0);
            Pos enricher = f.machine("enrichment_chamber", 1, 0);
            f.wire();
            f.carry(crusher, enricher);
            f.at(crusher).insertInput(s("mekanism:iron_clump", 5));
            if (variant == 1) {
                f.at(crusher).addUpgrades(true, 8);
                f.at(enricher).addUpgrades(true, 8);
            }
            f.run(60_000, 0, () -> f.output(enricher, "mekanism:iron_dust") >= 5);
            assertEquals(5, f.output(enricher, "mekanism:iron_dust"));
            ticks[variant] = f.world.ticks;
        }
        assertTrue(ticks[1] * 3 < ticks[0], "upgraded " + ticks[1] + " ticks, plain " + ticks[0]);
    }

    /** Without a generator the battery runs the line dry; every clump is exactly one thing at the end. */
    public static void batteryRunsDry(Types types) {
        Factory f = new Factory(types);
        Pos crusher = f.machine("crusher", 0, 0);
        Pos enricher = f.machine("enrichment_chamber", 1, 0);
        f.wire();
        f.carry(crusher, enricher);
        f.at(crusher).insertInput(s("mekanism:iron_clump", 40));
        Pos battery = TestWorld.at(-4, 0, 0);
        f.at(battery).energy().set(30_000);
        long before = f.world.totalEnergy();
        f.run(30_000, 0, () -> false);
        assertTrue(f.world.totalEnergy() < before, "something was made");
        int total = size(f.at(crusher).input()) + (f.at(crusher).running() ? 1 : 0) + size(f.at(crusher).output())
                + size(f.at(enricher).input()) + (f.at(enricher).running() ? 1 : 0) + size(f.at(enricher).output());
        assertEquals(40, total, "every clump is waiting, being crushed, dirty dust, being enriched, or dust");
    }

    private static int size(Stack stack) {
        return stack == null ? 0 : stack.count();
    }
}
