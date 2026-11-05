package com.aitchn.prism.validation.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.testkit.ModTestHarness;
import com.aitchn.prism.validation.mekanism.logic.Chemical;
import com.aitchn.prism.validation.mekanism.logic.Machine;
import com.aitchn.prism.validation.mekanism.logic.MachineType;
import com.aitchn.prism.validation.mekanism.logic.Pos;
import com.aitchn.prism.validation.mekanism.logic.Recipe;
import com.aitchn.prism.validation.mekanism.logic.Stack;
import com.aitchn.prism.validation.mekanism.logic.TestWorld;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Whole production lines built from the real YAML recipes and block specs: energy from a battery through cables,
 * gas through pipes, items carried from machine to machine like a player would. Each run is repeated with every
 * machine saved and loaded again every few ticks; the result must be the same, and no item may be left behind.
 */
class FactoryChainTest {

    private static final class Mod implements PrismMod {
        @Override
        public void onInitialize(ModContext context) {
            Content.register(context);
        }
    }

    private static ModTestHarness harness;

    private static ModTestHarness harness() {
        if (harness == null) {
            harness = ModTestHarness.load("mekanism", new Mod(), Path.of("src/main/resources"));
        }
        return harness;
    }

    /** The machine type of block {@code id} with its recipes from the loaded content. */
    private static MachineType type(String id) {
        MachineBehavior spec = harness().block("mekanism:" + id).behaviors(MachineBehavior.class).getFirst();
        List<Recipe> recipes = new ArrayList<>();
        for (var entry : harness().recipes().all(Content.machineRecipes())) {
            if (entry.value().machine().equals(id)) {
                recipes.add(entry.value().toLogic());
            }
        }
        return MachineType.fixed(id, recipes, spec.energyCapacity(), spec.maxReceive(), spec.tankCapacity());
    }

    private static MachineType cube() {
        MachineBehavior spec = harness().block("mekanism:energy_cube").behaviors(MachineBehavior.class).getFirst();
        return new MachineType("energy_cube", () -> List.of(), spec.energyCapacity(), spec.maxReceive(), 0,
                spec.maxExtract());
    }

    /** A row of machines along x, a cable line above them, a pipe line below, and a charged battery at the end. */
    private static final class Line {
        final TestWorld world = new TestWorld();
        final List<Pos> stages = new ArrayList<>();
        final Pos battery;
        final long startEnergy;

        Line(String... machines) {
            int x = 0;
            for (String id : machines) {
                Pos pos = TestWorld.at(x++, 0, 0);
                world.placeMachine(pos, type(id));
                stages.add(pos);
            }
            battery = TestWorld.at(x, 0, 0);
            world.placeMachine(battery, cube());
            world.machine(battery).energy().set(1_500_000);
            startEnergy = world.totalEnergy();
            for (int i = 0; i <= x; i++) {
                world.placeCable(TestWorld.at(i, 1, 0));
                if (i < x) {
                    world.placePipe(TestWorld.at(i, -1, 0));
                }
            }
        }

        Machine at(int stage) {
            return world.machine(stages.get(stage));
        }

        /** Runs until {@code done} holds or the tick limit is hit, carrying items down the line every few ticks. */
        void run(int limit, int reloadEvery, java.util.function.BooleanSupplier done) {
            for (int tick = 1; tick <= limit && !done.getAsBoolean(); tick++) {
                world.tick();
                if (tick % 7 == 0) {
                    for (int i = 0; i + 1 < stages.size(); i++) {
                        world.shuttle(stages.get(i), stages.get(i + 1));
                    }
                }
                if (reloadEvery > 0 && tick % reloadEvery == 0) {
                    world.reloadAll();
                }
            }
        }

        int count(int stage, String item) {
            Stack out = at(stage).output();
            return out != null && out.id().equals(item) ? out.count() : 0;
        }

        void assertNothingLeftBehind(int lastStage) {
            for (int i = 0; i <= lastStage; i++) {
                if (i < lastStage) {
                    assertNull(at(i).output(), "stage " + i + " still holds output " + at(i).output());
                }
                assertNull(at(i).input(), "stage " + i + " still holds input " + at(i).input());
                assertNull(at(i).extra(), "stage " + i + " still holds a second input " + at(i).extra());
                assertTrue(!at(i).running(), "stage " + i + " is still running");
            }
            assertTrue(world.totalEnergy() <= startEnergy, "energy cannot grow without a generator");
        }
    }

    // ------------------------------------------------------------------------------------------------ 3x iron chain

    private static Line ironLine() {
        Line line = new Line("purification_chamber", "crusher", "enrichment_chamber", "energized_smelter",
                "electrolytic_separator");
        line.at(4).inTank().set(Chemical.WATER, 24); // a separator runs while it has water, so give it just enough
        line.at(0).insertInput(new Stack("minecraft:raw_iron", 4));
        return line;
    }

    @Test
    void fourRawIronBecomeTwelveIronIngotsThroughFourMachinesAndAGasPlant() {
        Line line = ironLine();
        line.run(60_000, 0, () -> line.count(3, "minecraft:iron_ingot") >= 12);
        assertEquals(12, line.count(3, "minecraft:iron_ingot"));
        line.assertNothingLeftBehind(3);
    }

    @Test
    void theSameIronLineWithEveryMachineSavedAndLoadedAgainEndsTheSame() {
        Line line = ironLine();
        line.run(60_000, 53, () -> line.count(3, "minecraft:iron_ingot") >= 12);
        assertEquals(12, line.count(3, "minecraft:iron_ingot"));
        line.assertNothingLeftBehind(3);
    }

    // ------------------------------------------------------------------------------------------------ 4x osmium chain

    @Test
    void twoOsmiumOreBecomeEightIngotsThroughTheFourTimesChain() {
        Line line = new Line("chemical_injection_chamber", "purification_chamber", "crusher", "enrichment_chamber",
                "energized_smelter", "electrolytic_separator");
        line.at(5).inTank().set(Chemical.WATER, 48);
        line.at(0).insertInput(new Stack("mekanism:osmium_ore", 2));
        line.run(80_000, 71, () -> line.count(4, "mekanism:osmium_ingot") >= 8);
        assertEquals(8, line.count(4, "mekanism:osmium_ingot"));
        line.assertNothingLeftBehind(4);
    }

    // ------------------------------------------------------------------------------------------------ steel

    @Test
    void ironIngotsAndCoalBecomeSteelThroughTwoInfusersAndASmelter() {
        Line line = new Line("metallurgic_infuser", "metallurgic_infuser", "energized_smelter");
        line.at(0).insertInput(new Stack("minecraft:iron_ingot", 3));
        line.at(0).insertExtra(new Stack("minecraft:coal", 3));
        line.at(1).insertExtra(new Stack("minecraft:coal", 3));
        line.run(60_000, 43, () -> line.count(2, "mekanism:steel_ingot") >= 3);
        assertEquals(3, line.count(2, "mekanism:steel_ingot"));
        line.assertNothingLeftBehind(2);
    }

    // ------------------------------------------------------------------------------------------------ upgrades

    @Test
    void upgradedMachinesFinishTheLineSooner() {
        Line slow = new Line("crusher", "enrichment_chamber");
        Line fast = new Line("crusher", "enrichment_chamber");
        for (Line line : List.of(slow, fast)) {
            line.at(0).insertInput(new Stack("mekanism:iron_clump", 5));
        }
        fast.at(0).addUpgrades(true, 8);
        fast.at(1).addUpgrades(true, 8);
        slow.run(60_000, 0, () -> slow.count(1, "mekanism:iron_dust") >= 5);
        fast.run(60_000, 0, () -> fast.count(1, "mekanism:iron_dust") >= 5);
        assertEquals(5, slow.count(1, "mekanism:iron_dust"));
        assertEquals(5, fast.count(1, "mekanism:iron_dust"));
        assertTrue(fast.world.ticks * 3 < slow.world.ticks, "fast " + fast.world.ticks + " slow " + slow.world.ticks);
    }

    // ------------------------------------------------------------------------------------------------ energy

    @Test
    void aBatteryRunsTheLineThenStopsWhenEmptyAndNothingIsLost() {
        Line line = new Line("crusher", "enrichment_chamber");
        line.at(0).insertInput(new Stack("mekanism:iron_clump", 40));
        line.world.machine(line.battery).energy().set(30_000);
        long before = line.world.totalEnergy();
        line.run(30_000, 0, () -> false);
        assertTrue(line.world.totalEnergy() < before, "something was made");
        assertTrue(line.world.machine(line.battery).energy().stored() < 30_000);
        // every clump is exactly one thing: waiting, being crushed, dirty dust waiting, being enriched, or dust
        int total = size(line.at(0).input()) + (line.at(0).running() ? 1 : 0) + size(line.at(0).output())
                + size(line.at(1).input()) + (line.at(1).running() ? 1 : 0) + size(line.at(1).output());
        assertEquals(40, total, "items conserved");
    }

    private static int size(Stack stack) {
        return stack == null ? 0 : stack.count();
    }
}
