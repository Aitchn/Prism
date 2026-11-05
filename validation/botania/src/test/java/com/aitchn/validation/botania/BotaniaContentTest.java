package com.aitchn.validation.botania;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockBreakBehavior;
import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.block.BlockPlaceBehavior;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.testkit.ModTestHarness;
import com.aitchn.validation.botania.logic.FlowerType;
import com.aitchn.validation.botania.logic.Recipes;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Loads the real YAML through the real Prism lifecycle and checks it agrees with the rule tables. */
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
        assertEquals(28, harness.report().itemCount());
    }

    @Test
    void statefulBlocksReactToPlaceBreakAndInteract() {
        var harness = load();
        for (String name : List.of("mana_pool", "mana_spreader", "petal_apothecary", "daybloom", "endoflame",
                "pure_daisy")) {
            var block = harness.block("botania:" + name);
            assertEquals(1, block.behaviors(BlockPlaceBehavior.class).size(), name);
            assertEquals(1, block.behaviors(BlockBreakBehavior.class).size(), name);
            assertEquals(1, block.behaviors(BlockInteractBehavior.class).size(), name);
        }
        assertTrue(harness.block("botania:livingrock").behaviors().isEmpty());
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
        for (FlowerType type : FlowerType.values()) {
            assertTrue(load().manager().blocks().contains(PrismKey.parse("botania:" + type.id())), type.id());
        }
    }

    @Test
    void recipeTablesOnlyNameContentThatExists() {
        var harness = load();
        for (String dye : Recipes.DYES) {
            assertTrue(harness.manager().items().contains(PrismKey.parse(Recipes.petalItem(dye))), dye);
        }
        for (String input : List.of("minecraft:iron_ingot", "minecraft:ender_pearl", "minecraft:diamond")) {
            String result = Recipes.infusion(input).result();
            assertTrue(harness.manager().items().contains(PrismKey.parse(result)), result);
        }
        for (String block : List.of("minecraft:stone", "minecraft:oak_log")) {
            assertTrue(harness.manager().blocks().contains(PrismKey.parse(Recipes.conversion(block))), block);
        }
        for (String flower : List.of("botania:daybloom", "botania:endoflame", "botania:pure_daisy")) {
            assertTrue(harness.manager().items().contains(PrismKey.parse(flower)), flower);
        }
        assertFalse(harness.manager().items().contains(PrismKey.parse("botania:does_not_exist")));
        assertTrue(harness.manager().items().contains(PrismKey.parse(BotaniaRuntime.WAND)));
    }
}
