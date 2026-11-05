package com.aitchn.validation.botania;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockCapabilityProvider;
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
import com.aitchn.validation.botania.logic.FlowerType;
import com.aitchn.validation.botania.logic.RecipeBook;
import com.aitchn.validation.botania.logic.Recipes;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Loads the real YAML (content and recipes) through the real Prism lifecycle and checks it agrees with the rules. */
class BotaniaContentTest {

    private static ModTestHarness load() {
        PrismMod mod = new PrismMod() {
            @Override
            public void onInitialize(ModContext context) {
                BotaniaContent.register(context, () -> null);
            }
        };
        var harness = ModTestHarness.load("botania", mod, Path.of("src/main/resources"));
        assertTrue(harness.report().active(), harness.report().problems().toString());
        return harness;
    }

    @Test
    void allContentLoads() {
        var harness = load();
        assertEquals(8, harness.report().blockCount());
        assertEquals(29, harness.report().itemCount());
        var recipes = harness.recipes();
        assertEquals(5, recipes.all(PrismRecipes.SHAPED).size());
        assertEquals(16, recipes.all(PrismRecipes.SHAPELESS).size());
        assertEquals(3, recipes.all(BotaniaContent.INFUSION).size());
        assertEquals(3, recipes.all(BotaniaContent.PETAL).size());
        assertEquals(2, recipes.all(BotaniaContent.PURE_DAISY).size());
    }

    @Test
    void statefulBlocksUseTheBlockPlatform() {
        var harness = load();
        for (String name : List.of("mana_pool", "mana_spreader", "petal_apothecary", "daybloom", "endoflame",
                "pure_daisy")) {
            var block = harness.block("botania:" + name);
            assertEquals(1, block.behaviors(BlockPlaceBehavior.class).size(), name);
            assertEquals(1, block.behaviors(BlockLoadBehavior.class).size(), name);
            assertEquals(1, block.behaviors(BlockTickBehavior.class).size(), name);
            assertEquals(1, block.behaviors(BlockRemoveBehavior.class).size(), name);
            assertEquals(1, block.behaviors(BlockInteractBehavior.class).size(), name);
        }
        assertEquals(1, harness.block("botania:mana_pool").behaviors(BlockCapabilityProvider.class).size());
        assertEquals(1, harness.block("botania:mana_spreader").behaviors(BlockCapabilityProvider.class).size());
        assertTrue(harness.block("botania:livingrock").behaviors().isEmpty());
        assertEquals(PrismComponents.Rotation.ALL,
                harness.block("botania:mana_spreader").components().get(PrismComponents.ROTATION).orElseThrow());
        assertEquals(0.0, harness.block("botania:daybloom").components().get(PrismComponents.MINING).orElseThrow()
                .hardness());
    }

    @Test
    void tabletComputesItsLore() {
        var harness = load();
        assertEquals(1, harness.item("botania:mana_tablet").behaviors(ItemLoreBehavior.class).size());
    }

    @Test
    void everyBlockHasAnItemThatPlacesIt() {
        var harness = load();
        for (PrismKey key : harness.manager().blocks().keys()) {
            var placed = harness.item(key.toString()).components().get(PrismComponents.PLACES_BLOCK).orElseThrow();
            assertEquals(key, placed.block());
        }
    }

    @Test
    void flowerKindsMatchTheFlowerTypes() {
        var harness = load();
        for (FlowerType type : FlowerType.values()) {
            assertTrue(harness.manager().blocks().contains(PrismKey.parse("botania:" + type.id())), type.id());
        }
    }

    @Test
    void yamlRecipesGiveTheSameBookAsTheRuleDefaults() {
        var harness = load();
        RecipeBook fromYaml = BotaniaRecipes.load(harness.recipes());
        RecipeBook defaults = RecipeBook.defaults();
        for (String input : List.of("minecraft:iron_ingot", "minecraft:ender_pearl", "minecraft:diamond")) {
            assertEquals(defaults.infusion(input), fromYaml.infusion(input), input);
        }
        for (String block : List.of("minecraft:stone", "minecraft:oak_log")) {
            assertEquals(defaults.conversion(block), fromYaml.conversion(block), block);
        }
        for (String flower : List.of("daybloom", "endoflame", "pure_daisy")) {
            var petals = harness.recipes().all(BotaniaContent.PETAL).stream()
                    .filter(r -> r.value().result().item().toString().equals("botania:" + flower))
                    .findFirst().orElseThrow().value().petals().stream().map(p -> p.item().toString()).toList();
            assertEquals("botania:" + flower, fromYaml.petal(petals));
            assertEquals("botania:" + flower, defaults.petal(petals));
        }
        assertNull(fromYaml.infusion("minecraft:dirt"));
    }

    @Test
    void recipesOnlyNameContentThatExists() {
        var harness = load();
        Set<PrismKey> items = harness.manager().items().keys();
        for (var recipe : harness.recipes().all(BotaniaContent.INFUSION)) {
            assertTrue(known(items, recipe.value().result().item()), recipe.id().toString());
        }
        for (var recipe : harness.recipes().all(BotaniaContent.PETAL)) {
            assertTrue(known(items, recipe.value().result().item()), recipe.id().toString());
            recipe.value().petals().forEach(p -> assertTrue(known(items, p.item()), p.toString()));
        }
        for (var recipe : harness.recipes().all(BotaniaContent.PURE_DAISY)) {
            assertTrue(known(harness.manager().blocks().keys(), recipe.value().result()), recipe.id().toString());
        }
        for (var recipe : harness.recipes().all(PrismRecipes.SHAPED)) {
            assertTrue(known(items, recipe.value().result().item()), recipe.id().toString());
            recipe.value().key().values().forEach(i -> assertTrue(known(items, i.item()), i.toString()));
        }
        for (String dye : Recipes.DYES) {
            assertTrue(items.contains(PrismKey.parse(Recipes.petalItem(dye))), dye);
        }
        assertTrue(items.contains(PrismKey.parse(BotaniaRuntime.WAND)));
        assertTrue(items.contains(PrismKey.parse(BotaniaRuntime.TABLET)));
    }

    /** Every item must be reachable in survival: crafted, infused, grown in an apothecary or dropped by a block. */
    @Test
    void everyItemIsObtainableInSurvival() {
        var harness = load();
        Set<PrismKey> obtainable = new HashSet<>();
        var recipes = harness.recipes();
        recipes.all(PrismRecipes.SHAPED).forEach(r -> obtainable.add(r.value().result().item()));
        recipes.all(PrismRecipes.SHAPELESS).forEach(r -> obtainable.add(r.value().result().item()));
        recipes.all(BotaniaContent.INFUSION).forEach(r -> obtainable.add(r.value().result().item()));
        recipes.all(BotaniaContent.PETAL).forEach(r -> obtainable.add(r.value().result().item()));
        // A block a Pure Daisy makes drops its item.
        recipes.all(BotaniaContent.PURE_DAISY).forEach(r -> harness.block(r.value().result().toString()).drop()
                .ifPresent(obtainable::add));
        for (PrismKey item : harness.manager().items().keys()) {
            assertTrue(obtainable.contains(item), item + " cannot be obtained in survival");
        }
        assertNotNull(obtainable);
    }

    private static boolean known(Set<PrismKey> content, PrismKey key) {
        return key.namespace().equals(PrismKey.MINECRAFT_NAMESPACE) || content.contains(key);
    }
}
