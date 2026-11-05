package com.aitchn.prism.validation.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.block.BlockLoadBehavior;
import com.aitchn.prism.api.block.BlockPlaceBehavior;
import com.aitchn.prism.api.block.BlockRemoveBehavior;
import com.aitchn.prism.api.block.BlockTickBehavior;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.item.ItemLoreBehavior;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.api.recipe.PrismRecipes;
import com.aitchn.prism.testkit.ModTestHarness;
import com.aitchn.prism.validation.ae2.grid.NodeKind;
import com.aitchn.prism.validation.ae2.storage.StorageCell;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

/** Loads the real content through the real Prism lifecycle, without a server. */
class Ae2ContentTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    private static final class Mod implements PrismMod {
        @Override
        public void onInitialize(@NotNull ModContext context) {
            Ae2Content.bind(context.registerComponent("energy", Ae2Content.Energy.class,
                    section -> new Ae2Content.Energy(section.requireInt("value"))));
            context.registerBehavior("node", NodeBehavior.class, section -> NodeBehavior.fromYaml(section, null));
            context.registerBehavior("tick", ControllerTickBehavior.class,
                    section -> ControllerTickBehavior.fromYaml(section, null));
            context.registerBehavior("energy_sink", EnergyCellBehavior.class,
                    section -> EnergyCellBehavior.fromYaml(section, null));
            context.registerBehavior("cell", ItemBehaviors.CellLore.class, ItemBehaviors.CellLore::fromYaml);
            context.registerBehavior("pattern", ItemBehaviors.PatternLore.class, ItemBehaviors.PatternLore::fromYaml);
        }
    }

    private static ModTestHarness load() {
        ModTestHarness harness = ModTestHarness.load("ae2", new Mod(), RESOURCES);
        assertTrue(harness.report().active(), harness.report().problems().toString());
        return harness;
    }

    @Test
    void everyNodeKindHasABlockWithTheRightBehavior() {
        var harness = load();
        assertEquals(Ae2Plugin.EXPECTED_BLOCKS, harness.report().blockCount());
        assertEquals(Ae2Plugin.EXPECTED_ITEMS, harness.report().itemCount());
        for (NodeKind kind : NodeKind.values()) {
            var block = harness.block("ae2:" + kind.id());
            var nodes = block.behaviors(NodeBehavior.class);
            assertEquals(1, nodes.size(), kind.id());
            assertEquals(kind, nodes.get(0).kind());
            assertEquals(1, block.behaviors(BlockPlaceBehavior.class).size());
            assertEquals(1, block.behaviors(BlockRemoveBehavior.class).size());
            assertEquals(1, block.behaviors(BlockLoadBehavior.class).size());
            assertEquals(1, block.behaviors(BlockInteractBehavior.class).size());
            assertEquals(kind == NodeKind.CONTROLLER ? 1 : 0, block.behaviors(BlockTickBehavior.class).size());
        }
    }

    @Test
    void blockItemsPlaceTheirBlockAndHaveTextures() {
        var harness = load();
        for (NodeKind kind : NodeKind.values()) {
            var item = harness.item("ae2:" + kind.id());
            assertEquals("ae2:" + kind.id(),
                    item.components().get(PrismComponents.PLACES_BLOCK).orElseThrow().block().toString());
            assertTrue(Files.exists(RESOURCES.resolve("assets/ae2/textures/item/" + kind.id() + ".png")));
            assertTrue(Files.exists(RESOURCES.resolve("assets/ae2/textures/block/" + kind.id() + ".png")));
        }
    }

    @Test
    void variantsHaveTheirTextures() {
        var harness = load();
        for (String id : new String[] {"controller", "drive", "terminal", "pattern_provider"}) {
            var variants = harness.block("ae2:" + id).components().get(PrismComponents.VARIANTS).orElseThrow();
            assertEquals(2, variants.names().size(), id);
            for (String variant : variants.names()) {
                assertTrue(Files.exists(RESOURCES.resolve("assets/ae2/textures/block/" + id + "_" + variant + ".png")),
                        id + " " + variant);
            }
        }
    }

    @Test
    void cellsPatternsAndCrystalsLoadWithTheirLoreAndEnergy() {
        var harness = load();
        for (StorageCell.Tier tier : StorageCell.Tier.values()) {
            var cell = harness.item(Ae2Content.cellKey(tier).toString());
            assertEquals(1, cell.behaviors(ItemLoreBehavior.class).size(), tier.name());
        }
        assertEquals(1, harness.item("ae2:encoded_pattern").behaviors(ItemLoreBehavior.class).size());
        assertEquals(1600, harness.item("ae2:charged_certus_quartz_crystal").components()
                .get(Ae2Content.energy).orElseThrow().value());
    }

    @Test
    void everyItemCanBeObtainedInSurvival() {
        var harness = load();
        Set<String> craftable = new HashSet<>();
        for (var recipe : harness.recipes().all(PrismRecipes.SHAPED)) {
            craftable.add(recipe.value().result().item().toString());
        }
        for (var recipe : harness.recipes().all(PrismRecipes.SHAPELESS)) {
            craftable.add(recipe.value().result().item().toString());
        }
        for (var item : harness.manager().items().keys()) {
            String key = item.toString();
            boolean encoded = key.equals("ae2:encoded_pattern"); // made from a blank pattern in the encoder
            assertTrue(craftable.contains(key) || encoded, key + " has no crafting recipe");
        }
    }

    @Test
    void recipeIngredientsExist() {
        var harness = load();
        Set<PrismKey> known = new HashSet<>(harness.manager().items().keys());
        for (var recipe : harness.recipes().all(PrismRecipes.SHAPED)) {
            recipe.value().key().values().forEach(ingredient -> assertTrue(
                    ingredient.item().namespace().equals("minecraft") || known.contains(ingredient.item()),
                    recipe.id() + " uses unknown " + ingredient));
        }
        for (var recipe : harness.recipes().all(PrismRecipes.SHAPELESS)) {
            recipe.value().ingredients().forEach(ingredient -> assertTrue(
                    ingredient.item().namespace().equals("minecraft") || known.contains(ingredient.item()),
                    recipe.id() + " uses unknown " + ingredient));
        }
    }
}
