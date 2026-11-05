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
        }
    }

    @Test
    void rubyLoadsWithComponentsAndHealBehavior() {
        var harness = ModTestHarness.load("examplemod", new Mod(), Path.of("src/main/resources"));
        assertTrue(harness.report().active(), harness.report().problems().toString());

        ItemDefinition ruby = harness.item("examplemod:ruby");
        assertEquals(2, ruby.components().get(ExampleMod.tier).orElseThrow().level());
        assertEquals("<red>Ruby", ruby.components().get(PrismComponents.DISPLAY_NAME).orElseThrow().miniMessage());
        assertEquals(1, ruby.behaviors(ItemUseBehavior.class).size());
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
        assertEquals(java.util.List.of("off", "on"),
                counter.components().get(PrismComponents.VARIANTS).orElseThrow().names());
        assertEquals(20, counter.behaviors(com.aitchn.prism.api.block.BlockTickBehavior.class).getFirst()
                .tickInterval());
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
        ruby.behaviors(ItemUseBehavior.class).forEach(behavior -> behavior.onUse(context));

        assertEquals(14.0, fake.health());
        assertTrue(cancelled[0]);
    }
}
