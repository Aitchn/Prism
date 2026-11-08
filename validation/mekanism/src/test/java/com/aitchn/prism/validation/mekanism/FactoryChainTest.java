package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.testkit.ModTestHarness;
import com.aitchn.prism.validation.mekanism.logic.Chains;
import com.aitchn.prism.validation.mekanism.logic.MachineType;
import com.aitchn.prism.validation.mekanism.logic.Recipe;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Whole production lines built from the real YAML recipes and block specs (the scenarios live in {@link Chains}):
 * energy from a battery through cables, gas through pipes whose faces are set to the right tanks, items carried from
 * machine to machine like a player would. Each scenario is repeated with every machine saved and loaded again every few
 * ticks; the result must be the same, and no item may be left behind.
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

    private static final Chains.Types TYPES = new Chains.Types() {
        @Override
        public MachineType machine(String id) {
            MachineBehavior spec = harness().block("mekanism:" + id).behaviors(MachineBehavior.class).getFirst();
            List<Recipe> recipes = new ArrayList<>();
            for (var entry : harness().recipes().all(Content.machineRecipes())) {
                if (entry.value().machine().equals(id)) {
                    recipes.add(entry.value().toLogic());
                }
            }
            return new MachineType(id, () -> recipes, spec.energyCapacity(), spec.maxReceive(), spec.tankCapacity(),
                    spec.maxExtract(), spec.bufferFilter());
        }

        @Override
        public MachineType battery() {
            return machine("energy_cube");
        }
    };

    @Test
    void ironThreeTimes() {
        Chains.ironThreeTimes(TYPES, 0);
    }

    @Test
    void ironThreeTimesWithEveryMachineSavedAndLoadedAgain() {
        Chains.ironThreeTimes(TYPES, 53);
    }

    @Test
    void osmiumFourTimesWithHydrogenChlorideMadeOnSite() {
        Chains.osmiumFourTimes(TYPES, 0);
    }

    @Test
    void osmiumFourTimesWithSaves() {
        Chains.osmiumFourTimes(TYPES, 71);
    }

    @Test
    void oneOsmiumOreBecomesFiveIngotsThroughTheWholeChemicalChain() {
        Chains.osmiumFiveTimes(TYPES, 0);
    }

    @Test
    void theWholeChemicalChainWithSavesKeepsFacesTanksAndProgress() {
        Chains.osmiumFiveTimes(TYPES, 61);
    }

    @Test
    void steelFromIronAndCoal() {
        Chains.steel(TYPES, 43);
    }

    @Test
    void upgradedMachinesFinishTheLineSooner() {
        Chains.upgrades(TYPES);
    }

    @Test
    void aBatteryRunsTheLineThenStopsWhenEmptyAndNothingIsLost() {
        Chains.batteryRunsDry(TYPES);
    }
}
