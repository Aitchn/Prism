package com.aitchn.prism.core.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.item.ItemUseBehavior;
import com.aitchn.prism.api.item.ItemUseContext;
import com.aitchn.prism.api.mod.ContentView;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.core.content.MemoryContentSource;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ModManagerTest {

    record Food(int nutrition) {}

    record Heal(int amount, AtomicInteger calls) implements ItemUseBehavior {
        @Override
        public void onUse(ItemUseContext context) {
            calls.incrementAndGet();
        }
    }

    record Lifecycle(List<String> log) implements com.aitchn.prism.api.block.BlockPlaceBehavior,
            com.aitchn.prism.api.block.BlockRemoveBehavior {
        @Override
        public void onPlace(com.aitchn.prism.api.block.BlockContext context) {
            log.add("place");
        }

        @Override
        public void onRemove(com.aitchn.prism.api.block.BlockContext context) {
            log.add("remove");
        }
    }

    static final List<String> LIFECYCLE_LOG = new ArrayList<>();

    static final AtomicInteger HEAL_CALLS = new AtomicInteger();

    static class TestMod implements PrismMod {
        final List<String> events = new ArrayList<>();

        @Override
        public void onInitialize(ModContext ctx) {
            events.add("init");
            ctx.registerComponent("food", Food.class, s -> new Food(s.requireInt("nutrition")));
            ctx.registerBehavior("lifecycle", Lifecycle.class, s -> new Lifecycle(LIFECYCLE_LOG));
            ctx.registerBehavior("heal", Heal.class, s -> {
                s.rejectUnknownKeys(java.util.Set.of("amount"));
                return new Heal(s.requireInt("amount"), HEAL_CALLS);
            });
        }

        @Override
        public void onContentLoaded(ContentView view) {
            events.add("loaded:" + view.items().size());
        }

        @Override
        public void onDisable() {
            events.add("disable");
        }
    }

    private static ModCandidate candidate(String id, String ns, MemoryContentSource content, PrismMod entry) {
        return new ModCandidate(id, ns, "prism", content, entry, Logger.getLogger("test"));
    }

    private static ModManager manager() {
        return new ModManager(key -> key.namespace().equals("minecraft"), key -> key.namespace().equals("minecraft"));
    }

    private static final String RUBY = """
            base: minecraft:paper
            components:
              prism:display_name: "<red>Ruby"
              prism:max_stack_size: 16
              testmod:food:
                nutrition: 4
            behaviors:
              - type: testmod:heal
                amount: 6
            """;

    @Test
    void validModPublishesItemWithComponentsAndBehaviors() {
        ModManager manager = manager();
        TestMod mod = new TestMod();
        var report = manager.activate(candidate("TestMod", "testmod",
                new MemoryContentSource().put("prism/items/ruby.yml", RUBY), mod));

        assertTrue(report.active(), report.problems().toString());
        ItemDefinition ruby = manager.items().require(new PrismKey("testmod", "ruby"));
        assertEquals(new PrismKey("minecraft", "paper"), ruby.base());
        assertEquals("<red>Ruby", ruby.components().get(PrismComponents.DISPLAY_NAME).orElseThrow().miniMessage());
        assertEquals(16, ruby.components().get(PrismComponents.MAX_STACK_SIZE).orElseThrow().value());
        assertEquals(1, ruby.behaviors(ItemUseBehavior.class).size());
        assertEquals(List.of("init", "loaded:1"), mod.events);
    }

    @Test
    void nestedFilesBecomeFolderKeys() {
        ModManager manager = manager();
        manager.activate(candidate("TestMod", "testmod", new MemoryContentSource()
                .put("prism/items/gems/ruby.yml", "base: paper\n"), new TestMod()));
        assertTrue(manager.items().contains(new PrismKey("testmod", "gems/ruby")));
    }

    @Test
    void invalidContentPublishesNothingAndReportsEveryProblem() {
        ModManager manager = manager();
        var report = manager.activate(candidate("TestMod", "testmod", new MemoryContentSource()
                .put("prism/items/good.yml", RUBY)
                .put("prism/items/missing_base.yml", "components: {}\n")
                .put("prism/items/unknown_type.yml", "base: paper\ncomponents:\n  other:thing: 1\n")
                .put("prism/items/bad_value.yml", "base: paper\ncomponents:\n  testmod:food:\n    nutrition: lots\n")
                .put("prism/items/bad_base.yml", "base: other:thing\n")
                .put("prism/items/broken.yml", "base: [unclosed\n"), new TestMod()));

        assertFalse(report.active());
        assertEquals(5, report.problems().size(), report.problems().toString());
        assertEquals(0, manager.items().size());
        assertFalse(manager.componentTypes().contains(new PrismKey("testmod", "food")), "types must roll back too");
        assertTrue(manager.report("TestMod").isPresent());
    }

    @Test
    void failingModDoesNotAffectOthers() {
        ModManager manager = manager();
        manager.activate(candidate("A", "a", new MemoryContentSource().put("prism/items/x.yml", "base: paper\n"), new TestMod()));
        var bad = manager.activate(candidate("B", "b", new MemoryContentSource().put("prism/items/x.yml", "nonsense: 1\n"), new TestMod()));
        assertFalse(bad.active());
        assertTrue(manager.items().contains(new PrismKey("a", "x")));
        assertTrue(manager.isActive("A"));
        assertFalse(manager.isActive("B"));
    }

    @Test
    void laterModCanUseTypesOfEarlierMod() {
        ModManager manager = manager();
        manager.activate(candidate("TestMod", "testmod", new MemoryContentSource(), new TestMod()));
        var report = manager.activate(candidate("Addon", "addon", new MemoryContentSource()
                .put("prism/items/bun.yml", "base: bread\ncomponents:\n  testmod:food:\n    nutrition: 2\n"),
                ctx -> {}));
        assertTrue(report.active(), report.problems().toString());
    }

    @Test
    void deactivateRemovesEverythingAndCallsOnDisable() {
        ModManager manager = manager();
        TestMod mod = new TestMod();
        manager.activate(candidate("TestMod", "testmod", new MemoryContentSource().put("prism/items/ruby.yml", RUBY), mod));
        manager.deactivate("TestMod");

        assertEquals(0, manager.items().size());
        assertFalse(manager.behaviorTypes().contains(new PrismKey("testmod", "heal")));
        assertTrue(manager.componentTypes().contains(PrismComponents.DISPLAY_NAME.key()), "built-ins stay");
        assertEquals("disable", mod.events.get(mod.events.size() - 1));
        // The namespace is free again.
        assertTrue(manager.activate(candidate("TestMod", "testmod", new MemoryContentSource(), new TestMod())).active());
    }

    @Test
    void namespaceCannotBeSharedOrReserved() {
        ModManager manager = manager();
        manager.activate(candidate("One", "shared", new MemoryContentSource(), ctx -> {}));
        assertFalse(manager.activate(candidate("Two", "shared", new MemoryContentSource(), ctx -> {})).active());
        assertFalse(manager.activate(candidate("Three", "prism", new MemoryContentSource(), ctx -> {})).active());
        assertFalse(manager.activate(candidate("Four", "minecraft", new MemoryContentSource(), ctx -> {})).active());
    }

    @Test
    void initializeThatThrowsFailsOnlyThatMod() {
        ModManager manager = manager();
        var report = manager.activate(candidate("Boom", "boom", new MemoryContentSource(), ctx -> {
            throw new IllegalStateException("nope");
        }));
        assertFalse(report.active());
        assertTrue(report.problems().get(0).message().contains("nope"));
    }

    @Test
    void dispatcherRunsUseBehaviorsByCapabilityAndSurvivesFailures() {
        ModManager manager = manager();
        HEAL_CALLS.set(0);
        manager.activate(candidate("TestMod", "testmod", new MemoryContentSource().put("prism/items/ruby.yml", RUBY), new TestMod()));
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> method.getName().equals("getUniqueId") ? new UUID(0, 1) : null);

        boolean[] owned = {true};
        var dispatcher = new BehaviorDispatcher(manager.items(), manager.blocks(), p -> owned[0], l -> owned[0], Logger.getLogger("test"));
        PrismKey ruby = new PrismKey("testmod", "ruby");

        assertEquals(1, dispatcher.dispatchItemUse(ruby, player, def -> null));
        assertEquals(1, HEAL_CALLS.get());
        assertEquals(0, dispatcher.dispatchItemUse(new PrismKey("testmod", "nothing"), player, def -> null));
        owned[0] = false;
        assertEquals(0, dispatcher.dispatchItemUse(ruby, player, def -> null), "wrong thread must not run behaviors");
        assertEquals(1, HEAL_CALLS.get());
    }

    @Test
    void blocksAreLoadedWithDropAndPlaceComponent() {
        ModManager manager = manager();
        var report = manager.activate(candidate("TestMod", "testmod", new MemoryContentSource()
                .put("prism/items/ruby_block.yml",
                        "base: paper\ncomponents:\n  prism:places_block: testmod:ruby_block\n  prism:model: testmod:item/ruby\n")
                .put("prism/blocks/ruby_block.yml",
                        "base: barrier\ndrop: ruby_block\ncomponents:\n  prism:model: testmod:block/ruby_block\n")
                .put("assets/testmod/textures/item/ruby.png", new byte[] {1, 2, 3}), new TestMod()));
        assertTrue(report.active(), report.problems().toString());
        var block = manager.blocks().require(new PrismKey("testmod", "ruby_block"));
        assertEquals(new PrismKey("minecraft", "barrier"), block.carrier());
        assertEquals(new PrismKey("testmod", "ruby_block"), block.drop().orElseThrow());
        var item = manager.items().require(new PrismKey("testmod", "ruby_block"));
        assertEquals(new PrismKey("testmod", "ruby_block"),
                item.components().get(PrismComponents.PLACES_BLOCK).orElseThrow().block());
        assertEquals(1, report.blockCount());
        assertEquals(3, manager.assets().get("TestMod").get("assets/testmod/textures/item/ruby.png").length);
        manager.deactivate("TestMod");
        assertEquals(0, manager.blocks().size());
        assertTrue(manager.assets().isEmpty());
    }

    @Test
    void blockPlaceAndRemoveBehaviorsAreDispatched() {
        ModManager manager = manager();
        LIFECYCLE_LOG.clear();
        var report = manager.activate(candidate("TestMod", "testmod", new MemoryContentSource()
                .put("prism/blocks/node.yml", "base: barrier\nbehaviors:\n  - type: testmod:lifecycle\n"),
                new TestMod()));
        assertTrue(report.active(), report.problems().toString());
        boolean[] owned = {true};
        var dispatcher = new BehaviorDispatcher(manager.items(), manager.blocks(), p -> true, l -> owned[0],
                Logger.getLogger("test"));
        PrismKey node = new PrismKey("testmod", "node");
        assertEquals(1, dispatcher.dispatchBlockPlace(node, null, def -> null));
        assertEquals(1, dispatcher.dispatchBlockRemove(node, null, def -> null));
        assertEquals(List.of("place", "remove"), LIFECYCLE_LOG);
        owned[0] = false;
        assertEquals(0, dispatcher.dispatchBlockPlace(node, null, def -> null));
    }

    @Test
    void bbmodelsAreCollectedByKeyAndReleasedOnDeactivate() {
        ModManager manager = manager();
        manager.activate(candidate("TestMod", "testmod", new MemoryContentSource()
                .put("prism/models/turret.bbmodel", "{}")
                .put("prism/models/guns/rifle.bbmodel", "{}")
                .put("prism/models/readme.txt", "x"), new TestMod()));
        assertEquals(2, manager.models().size());
        assertTrue(manager.models().containsKey(new PrismKey("testmod", "guns/rifle")));
        manager.deactivate("TestMod");
        assertTrue(manager.models().isEmpty());
    }
}
