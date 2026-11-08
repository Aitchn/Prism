package com.aitchn.prism.validation.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockCapabilityProvider;
import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.block.BlockTickBehavior;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.item.ItemLoreBehavior;
import com.aitchn.prism.api.item.ItemUseBehavior;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.api.recipe.Ingredient;
import com.aitchn.prism.api.recipe.PrismRecipes;
import com.aitchn.prism.testkit.ModTestHarness;
import com.aitchn.prism.validation.mekanism.logic.Machine;
import com.aitchn.prism.validation.mekanism.logic.MachineType;
import com.aitchn.prism.validation.mekanism.logic.Recipe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The YAML content of the validation mod loads through the real Prism lifecycle, and the whole thing hangs together. */
class MekanismContentTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final List<String> MACHINES = List.of("crusher", "enrichment_chamber", "purification_chamber",
            "chemical_injection_chamber", "electrolytic_separator", "osmium_compressor", "combiner",
            "metallurgic_infuser", "precision_sawmill", "energized_smelter", "chemical_oxidizer", "chemical_infuser",
            "chemical_washer", "chemical_crystallizer", "chemical_dissolution_chamber",
            "pressurized_reaction_chamber");
    private static final java.util.Map<String, String> FACTORIES = java.util.Map.of(
            "smelting", "energized_smelter", "enriching", "enrichment_chamber", "crushing", "crusher",
            "compressing", "osmium_compressor", "combining", "combiner", "purifying", "purification_chamber",
            "injecting", "chemical_injection_chamber", "infusing", "metallurgic_infuser", "sawing", "precision_sawmill");
    private static final java.util.Map<String, Integer> LANES = java.util.Map.of("", 3, "advanced_", 5, "elite_", 7,
            "ultimate_", 9);
    private static final List<String> TANKS = List.of("chemical_tank", "fluid_tank");
    private static final List<String> CUBES = List.of("energy_cube", "advanced_energy_cube", "elite_energy_cube",
            "ultimate_energy_cube");

    private static final class Mod implements PrismMod {
        @Override
        public void onInitialize(ModContext context) {
            Content.register(context);
        }
    }

    private static ModTestHarness load() {
        var harness = ModTestHarness.load("mekanism", new Mod(), RESOURCES);
        assertTrue(harness.report().active(), harness.report().problems().toString());
        return harness;
    }

    @Test
    void allBlocksAndItemsLoad() {
        var harness = load();
        assertEquals(count("prism/blocks"), harness.manager().blocks().size());
        assertEquals(count("prism/items"), harness.manager().items().size());
    }

    private static long count(String directory) {
        try (var files = Files.list(RESOURCES.resolve(directory))) {
            return files.filter(f -> f.toString().endsWith(".yml")).count();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long countRecursive(String directory) {
        try (var files = Files.walk(RESOURCES.resolve(directory))) {
            return files.filter(f -> f.toString().endsWith(".yml")).count();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void storageTanksHoldWhatTheirFilterAllows() {
        var harness = load();
        for (String id : TANKS) {
            MachineBehavior spec = harness.block("mekanism:" + id).behaviors(MachineBehavior.class).getFirst();
            assertTrue(spec.tankCapacity() >= 32_000, id);
            assertTrue(spec.bufferFilter() != null, id + " is a storage tank");
            assertTrue(spec.bufferFilter().test(com.aitchn.prism.validation.mekanism.logic.Chemical.WATER), id);
        }
        assertTrue(harness.block("mekanism:chemical_tank").behaviors(MachineBehavior.class).getFirst().bufferFilter()
                .test(new com.aitchn.prism.validation.mekanism.logic.Chemical("hydrogen")));
        assertFalse(harness.block("mekanism:fluid_tank").behaviors(MachineBehavior.class).getFirst().bufferFilter()
                .test(new com.aitchn.prism.validation.mekanism.logic.Chemical("hydrogen")));
    }

    @Test
    void energyCubesAreBatteries() {
        var harness = load();
        long previous = 0;
        for (String id : CUBES) {
            MachineBehavior spec = harness.block("mekanism:" + id).behaviors(MachineBehavior.class).getFirst();
            assertTrue(spec.maxExtract() > 0 && spec.maxReceive() > 0, id + " gives and takes energy");
            assertTrue(spec.energyCapacity() > previous, id + " holds more than the tier before");
            previous = spec.energyCapacity();
            assertEquals(1, harness.block("mekanism:" + id).behaviors(BlockCapabilityProvider.class).size(), id);
            assertTrue(Files.exists(RESOURCES.resolve("assets/mekanism/textures/block/" + id + ".png")), id);
        }
    }

    @Test
    void theDigitalMinerIsAMachineBlockWithTheMinerFlag() {
        var harness = load();
        var block = harness.block("mekanism:digital_miner");
        MachineBehavior spec = block.behaviors(MachineBehavior.class).getFirst();
        assertTrue(spec.miner());
        assertEquals(1, block.behaviors(BlockTickBehavior.class).size());
        assertEquals(1, block.behaviors(BlockCapabilityProvider.class).size());
        for (String look : List.of("idle", "active")) {
            assertTrue(Files.exists(RESOURCES.resolve("assets/mekanism/textures/block/digital_miner_" + look + ".png")));
        }
    }

    @Test
    void theTeleporterIsAMachineBlockWithTheTeleporterFlag() {
        var harness = load();
        MachineBehavior spec = harness.block("mekanism:teleporter").behaviors(MachineBehavior.class).getFirst();
        assertTrue(spec.teleporter());
        assertFalse(spec.miner());
        assertEquals(1, harness.block("mekanism:teleporter").behaviors(BlockInteractBehavior.class).size());
    }

    @Test
    void theDynamicValveIsTheCuboidControllerAndTheOtherWallsArePlain() {
        var harness = load();
        assertEquals(1, harness.block("mekanism:dynamic_valve").behaviors(ValveBehavior.class).size());
        assertTrue(harness.block("mekanism:dynamic_tank").behaviors(ValveBehavior.class).isEmpty());
        assertTrue(harness.block("mekanism:structural_glass").behaviors(ValveBehavior.class).isEmpty());
    }

    @Test
    void everyFactoryRunsTheRecipesOfItsMachineWithMoreLanesPerTier() {
        var harness = load();
        for (var kind : FACTORIES.entrySet()) {
            assertTrue(MACHINES.contains(kind.getValue()), kind.getValue());
            for (var tier : LANES.entrySet()) {
                String id = tier.getKey() + kind.getKey() + "_factory";
                MachineBehavior spec = harness.block("mekanism:" + id).behaviors(MachineBehavior.class).getFirst();
                assertEquals(kind.getValue(), spec.recipesOf(), id);
                assertEquals((int) tier.getValue(), spec.lanes(), id);
                assertEquals(id, spec.machine());
                assertEquals(1, harness.block("mekanism:" + id).behaviors(BlockTickBehavior.class).size(), id);
                for (String look : List.of("idle", "active")) {
                    assertTrue(Files.exists(RESOURCES.resolve("assets/mekanism/textures/block/" + id + "_" + look + ".png")), id);
                }
            }
        }
    }

    @Test
    void machineBlocksUsePlatformCapabilitiesAndHaveBothLooks() {
        var harness = load();
        for (String id : MACHINES) {
            var block = harness.block("mekanism:" + id);
            assertEquals(1, block.behaviors(MachineBehavior.class).size(), id);
            assertEquals(id, block.behaviors(MachineBehavior.class).getFirst().machine());
            assertEquals(1, block.behaviors(BlockTickBehavior.class).size(), id);
            assertEquals(1, block.behaviors(BlockInteractBehavior.class).size(), id);
            assertEquals(1, block.behaviors(BlockCapabilityProvider.class).size(), id);
            assertEquals(List.of("idle", "active"), block.components().get(PrismComponents.VARIANTS).orElseThrow().names());
            for (String look : List.of("idle", "active")) {
                assertTrue(Files.exists(RESOURCES.resolve("assets/mekanism/textures/block/" + id + "_" + look + ".png")),
                        id + " needs a " + look + " texture");
            }
            assertTrue(harness.item("mekanism:" + id).components().has(PrismComponents.PLACES_BLOCK));
        }
    }

    @Test
    void conduitsDoNotSwallowClicks() {
        var harness = load();
        assertEquals(1, harness.block("mekanism:universal_cable").behaviors(CableBehavior.class).size());
        assertEquals(1, harness.block("mekanism:pressurized_tube").behaviors(PipeBehavior.class).size());
        assertTrue(harness.block("mekanism:universal_cable").behaviors(BlockInteractBehavior.class).isEmpty());
        assertTrue(harness.block("mekanism:pressurized_tube").behaviors(BlockInteractBehavior.class).isEmpty());
    }

    @Test
    void energyTabletIsAnItemWithDataAndLore() {
        var tablet = load().item("mekanism:energy_tablet");
        assertEquals(1, tablet.behaviors(ItemUseBehavior.class).size());
        assertEquals(1, tablet.behaviors(ItemLoreBehavior.class).size());
        assertEquals(1_000_000, tablet.behaviors(TabletBehavior.class).getFirst().capacity());
    }

    @Test
    void everyItemAndBlockHasItsTexture() {
        var harness = load();
        for (PrismKey key : harness.manager().items().keys()) {
            if (isGauge(harness, key)) {
                continue; // Prism draws gauge icons
            }
            assertTrue(Files.exists(RESOURCES.resolve("assets/mekanism/textures/item/" + key.value() + ".png")),
                    "item texture for " + key);
        }
        for (PrismKey key : harness.manager().blocks().keys()) {
            var variants = harness.block(key.toString()).components().get(PrismComponents.VARIANTS);
            if (variants.isEmpty()) {
                assertTrue(Files.exists(RESOURCES.resolve("assets/mekanism/textures/block/" + key.value() + ".png")),
                        "block texture for " + key);
            }
        }
    }

    @Test
    void everyMachineRecipeNamesARealMachineAndRealItems() {
        var harness = load();
        var recipes = harness.recipes().all(Content.machineRecipes());
        assertEquals(countRecursive("prism/recipes/machine"), recipes.size());
        Set<String> machinesWithRecipes = new HashSet<>();
        for (var recipe : recipes) {
            MachineRecipe r = recipe.value();
            machinesWithRecipes.add(r.machine());
            assertTrue(MACHINES.contains(r.machine()), recipe.id() + " names an unknown machine " + r.machine());
            if (r.input() != null) {
                assertItemExists(harness, recipe.id(), r.input().item());
            }
            if (r.input2() != null) {
                assertItemExists(harness, recipe.id(), r.input2().item());
            }
            if (r.output() != null) {
                assertItemExists(harness, recipe.id(), r.output().item());
            }
        }
        assertEquals(Set.copyOf(MACHINES), machinesWithRecipes, "every machine has at least one recipe");
    }

    private static void assertItemExists(ModTestHarness harness, PrismKey recipe, PrismKey item) {
        assertTrue(item.namespace().equals("minecraft") || harness.manager().items().contains(item),
                recipe + " uses an item that does not exist: " + item);
    }

    @Test
    void everyMachineRecipeRunsOnTheMachineLogic() {
        var harness = load();
        for (var entry : harness.recipes().all(Content.machineRecipes())) {
            MachineRecipe r = entry.value();
            MachineBehavior spec = harness.block("mekanism:" + r.machine()).behaviors(MachineBehavior.class).getFirst();
            Recipe logic = r.toLogic();
            Machine machine = new Machine(MachineType.fixed(r.machine(), List.of(logic), spec.energyCapacity(),
                    spec.maxReceive(), spec.tankCapacity()));
            machine.energy().set(spec.energyCapacity());
            if (r.input() != null) {
                assertEquals(0, machine.insertInput(new com.aitchn.prism.validation.mekanism.logic.Stack(
                        r.input().item().toString(), r.input().count())).count(), entry.id() + " input accepted");
            }
            if (r.input2() != null) {
                assertEquals(0, machine.insertExtra(new com.aitchn.prism.validation.mekanism.logic.Stack(
                        r.input2().item().toString(), r.input2().count())).count(), entry.id() + " second input accepted");
            }
            if (r.chemicalIn() != null) {
                assertEquals(r.chemicalIn().amount(), machine.inTank().fill(r.chemicalIn().chemical(),
                        r.chemicalIn().amount(), false), entry.id() + " chemical accepted");
            }
            if (r.chemicalIn2() != null) {
                assertEquals(r.chemicalIn2().amount(), machine.inTank2().fill(r.chemicalIn2().chemical(),
                        r.chemicalIn2().amount(), false), entry.id() + " second chemical accepted");
            }
            for (int i = 0; i <= r.ticks() + 1; i++) {
                machine.energy().set(spec.energyCapacity()); // long recipes may need more than one buffer: cables refill it
                machine.tick();
            }
            if (r.output() != null) {
                assertNotNull(machine.output(), entry.id() + " produced nothing");
                assertEquals(r.output().item().toString(), machine.output().id(), entry.id().toString());
                assertEquals(r.output().count(), machine.output().count(), entry.id().toString());
            }
            if (!r.chemicalOut().isEmpty()) {
                assertEquals(r.chemicalOut().get(0).chemical(), machine.outTank().type(), entry.id().toString());
                assertEquals(r.chemicalOut().get(0).amount(), machine.outTank().amount(), entry.id().toString());
            }
            if (r.chemicalOut().size() > 1) {
                assertEquals(r.chemicalOut().get(1).chemical(), machine.outTank2().type(), entry.id().toString());
            }
        }
    }

    /**
     * Survival: starting from vanilla items only, following crafting, cooking and machine recipes, every item of the
     * mod can be made. (Machine energy and gas are not modelled; each has its own machine and generator in the chain.)
     */
    @Test
    void everyItemCanBeObtainedInSurvival() {
        var harness = load();
        var recipes = harness.recipes();
        Set<PrismKey> have = new HashSet<>();
        Set<String> gases = new HashSet<>(List.of("water")); // a bucket of water is always at hand
        boolean changed = true;
        while (changed) {
            changed = false;
            List<Step> steps = new ArrayList<>();
            recipes.all(PrismRecipes.SHAPED).forEach(r ->
                    steps.add(new Step(new ArrayList<>(r.value().key().values()), r.value().result().item())));
            recipes.all(PrismRecipes.SHAPELESS).forEach(r ->
                    steps.add(new Step(r.value().ingredients(), r.value().result().item())));
            for (var type : List.of(PrismRecipes.SMELTING, PrismRecipes.BLASTING, PrismRecipes.SMOKING,
                    PrismRecipes.CAMPFIRE_COOKING)) {
                recipes.all(type).forEach(r -> steps.add(new Step(List.of(r.value().input()), r.value().result().item())));
            }
            recipes.all(Content.machineRecipes()).forEach(r -> {
                List<String> needs = new ArrayList<>();
                if (r.value().chemicalIn() != null) {
                    needs.add(r.value().chemicalIn().chemical().id());
                }
                if (r.value().chemicalIn2() != null) {
                    needs.add(r.value().chemicalIn2().chemical().id());
                }
                List<String> makes = r.value().chemicalOut().stream().map(c -> c.chemical().id()).toList();
                List<Ingredient> in = new ArrayList<>();
                if (r.value().input() != null) {
                    in.add(r.value().input());
                }
                if (r.value().input2() != null) {
                    in.add(r.value().input2());
                }
                steps.add(new Step(in, r.value().output() == null ? null : r.value().output().item(), needs, makes));
            });
            for (Step step : steps) {
                boolean usable = step.inputs.stream().allMatch(i -> obtainable(i, have))
                        && gases.containsAll(step.needs);
                if (!usable) {
                    continue;
                }
                if (step.result != null && have.add(step.result)) {
                    changed = true;
                }
                if (gases.addAll(step.makes)) {
                    changed = true;
                }
            }
        }
        for (PrismKey item : harness.manager().items().keys()) {
            if (isGauge(harness, item)) {
                continue; // menu icons, never handed out
            }
            assertTrue(have.contains(item), item + " cannot be obtained in survival");
        }
        assertFalse(have.isEmpty());
    }

    /** One way to make something: needs items and gases, gives an item and/or gases. */
    private record Step(List<Ingredient> inputs, PrismKey result, List<String> needs, List<String> makes) {
        Step(List<Ingredient> inputs, PrismKey result) {
            this(inputs, result, List.of(), List.of());
        }
    }

    private static boolean obtainable(Ingredient ingredient, Set<PrismKey> have) {
        return ingredient.isTag() || ingredient.item().namespace().equals("minecraft") || have.contains(ingredient.item());
    }

    private static boolean isGauge(com.aitchn.prism.testkit.ModTestHarness harness, PrismKey key) {
        return harness.item(key.toString()).components().has(PrismComponents.GAUGE);
    }
}
