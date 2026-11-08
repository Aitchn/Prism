package com.example.prism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.item.ItemUseBehavior;
import com.aitchn.prism.api.item.ItemUseContext;
import com.aitchn.prism.testkit.FakePlayer;
import com.aitchn.prism.testkit.ModTestHarness;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** No server needed: the real content is loaded through the real Prism lifecycle. */
class ExampleModTest {

    private static final class Mod implements com.aitchn.prism.api.mod.PrismMod {
        @Override
        public void onInitialize(com.aitchn.prism.api.mod.ModContext context) {
            ExampleMod.tier = context.registerComponent("tier", ExampleMod.Tier.class,
                    section -> new ExampleMod.Tier(section.requireInt("value")));
            context.registerBehavior("heal", HealBehavior.class, HealBehavior::fromYaml);
            context.registerBehavior("counter", CounterBehavior.class, CounterBehavior::fromYaml);
            context.registerBehavior("use_counter", UseCounterBehavior.class, UseCounterBehavior::fromYaml);
            ExampleMod.polishing = context.registerRecipeType("polishing", Polishing.class, Polishing::decode);
        }
    }

    @Test
    void rubyLoadsWithComponentsAndHealBehavior() {
        var harness = ModTestHarness.load("examplemod", new Mod(), Path.of("src/main/resources"));
        assertTrue(harness.report().active(), harness.report().problems().toString());

        ItemDefinition ruby = harness.item("examplemod:ruby");
        assertEquals(2, ruby.components().get(ExampleMod.tier).orElseThrow().level());
        assertEquals("<red>Ruby", ruby.components().get(PrismComponents.DISPLAY_NAME).orElseThrow().miniMessage());
        assertEquals(2, ruby.behaviors(ItemUseBehavior.class).size());
        assertEquals(1, ruby.behaviors(com.aitchn.prism.api.item.ItemLoreBehavior.class).size());
        assertEquals(java.util.List.of("<gray>Right click to heal"),
                ruby.components().get(PrismComponents.LORE).orElseThrow().lines());
    }

    @Test
    void blockAndItsPlacingItemLoad() {
        var harness = ModTestHarness.load("examplemod", new Mod(), Path.of("src/main/resources"));
        assertTrue(harness.report().active(), harness.report().problems().toString());
        assertEquals("minecraft:barrier", harness.block("examplemod:ruby_block").carrier().toString());
        assertTrue(harness.item("examplemod:ruby_block").components()
                .has(PrismComponents.PLACES_BLOCK));
    }

    @Test
    void counterUsesTheBlockPlatform() {
        var harness = ModTestHarness.load("examplemod", new Mod(), Path.of("src/main/resources"));
        assertTrue(harness.report().active(), harness.report().problems().toString());
        var counter = harness.block("examplemod:ruby_counter");
        assertEquals(PrismComponents.Rotation.HORIZONTAL,
                counter.components().get(PrismComponents.ROTATION).orElseThrow());
        assertEquals(1.5, counter.components().get(PrismComponents.MINING).orElseThrow().hardness());
        assertEquals(java.util.List.of("idle", "active"),
                counter.components().get(PrismComponents.VARIANTS).orElseThrow().names());
        assertEquals(20, counter.behaviors(com.aitchn.prism.api.block.BlockTickBehavior.class).getFirst()
                .tickInterval());
        var hum = counter.components().get(PrismComponents.AMBIENT_SOUND).orElseThrow();
        assertEquals("examplemod:block.counter.hum", hum.sound().toString());
        assertTrue(hum.playsIn("active"));
        assertTrue(!hum.playsIn("idle"));
    }

    @Test
    void oreGeneratesInTheOverworld() {
        var harness = ModTestHarness.load("examplemod", new Mod(), Path.of("src/main/resources"));
        assertTrue(harness.report().active(), harness.report().problems().toString());
        var veins = harness.block("examplemod:ruby_ore").components().get(PrismComponents.ORE).orElseThrow().veins();
        assertEquals(1, veins.size());
        var vein = veins.getFirst();
        assertEquals("normal", vein.dimension());
        assertEquals(-48, vein.minY());
        assertEquals(40, vein.maxY());
        assertEquals(6, vein.veinsPerChunk());
        assertEquals(1, vein.rarity());
        assertEquals(6, vein.size());
        assertTrue(vein.replace().contains(com.aitchn.prism.api.PrismKey.parse("minecraft:deepslate")));
        assertEquals("examplemod:ruby", harness.block("examplemod:ruby_ore").drop().orElseThrow().toString());
    }

    @Test
    void cableConnectsToCablesAndCounters() {
        var harness = ModTestHarness.load("examplemod", new Mod(), Path.of("src/main/resources"));
        assertTrue(harness.report().active(), harness.report().problems().toString());
        var cable = harness.block("examplemod:ruby_cable");
        var connections = cable.components().get(PrismComponents.CONNECTIONS).orElseThrow();
        assertEquals(java.util.List.of("self", "examplemod:ruby_counter"), connections.connectsTo());
        assertEquals(PrismComponents.Connections.SIDES, connections.faces());
        assertEquals(64, connections.masks().size());
        assertEquals(64, com.aitchn.prism.core.block.Appearances.looks(cable).size());
        assertEquals("examplemod:ruby_cable", cable.drop().orElseThrow().toString());
    }

    @Test
    void recipesLoadForPrismAndModTypes() {
        var harness = ModTestHarness.load("examplemod", new Mod(), Path.of("src/main/resources"));
        assertTrue(harness.report().active(), harness.report().problems().toString());
        var recipes = harness.recipes();
        var block = recipes.get(com.aitchn.prism.api.recipe.PrismRecipes.SHAPED,
                com.aitchn.prism.api.PrismKey.parse("examplemod:ruby_block")).orElseThrow().value();
        assertEquals(java.util.List.of("RRR", "RRR", "RRR"), block.pattern());
        assertEquals("examplemod:ruby", block.key().get('R').item().toString());
        assertEquals(2, recipes.all(com.aitchn.prism.api.recipe.PrismRecipes.SHAPED).size());
        assertEquals(9, recipes.all(com.aitchn.prism.api.recipe.PrismRecipes.SHAPELESS).getFirst().value().result().count());
        assertEquals(1, recipes.all(com.aitchn.prism.api.recipe.PrismRecipes.SMELTING).size());
        var polish = recipes.all(ExampleMod.polishing);
        assertEquals(1, polish.size());
        assertEquals(100, polish.getFirst().value().ticks());
    }

    @Test
    void healBehaviorRestoresHealthAndCancelsVanillaAction() {
        var harness = ModTestHarness.load("examplemod", new Mod(), Path.of("src/main/resources"));
        ItemDefinition ruby = harness.item("examplemod:ruby");
        FakePlayer fake = new FakePlayer("alex");
        fake.player().setHealth(10);
        boolean[] cancelled = {false};

        ItemUseContext context = (ItemUseContext) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {ItemUseContext.class}, (proxy, method, args) ->
                        switch (method.getName()) {
                            case "player" -> fake.player();
                            case "definition" -> ruby;
                            case "cancelVanillaAction" -> {
                                cancelled[0] = true;
                                yield null;
                            }
                            default -> null;
                        });
        ruby.behaviors(HealBehavior.class).forEach(behavior -> behavior.onUse(context));

        assertEquals(14.0, fake.health());
        assertTrue(cancelled[0]);
    }
}
