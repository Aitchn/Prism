package com.aitchn.prism.validation.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.block.BlockPlaceBehavior;
import com.aitchn.prism.api.block.BlockRemoveBehavior;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.testkit.ModTestHarness;
import com.aitchn.prism.validation.ae2.grid.NodeKind;
import java.nio.file.Files;
import java.nio.file.Path;
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
        }
    }

    @Test
    void everyNodeKindHasABlockWithTheRightBehavior() {
        var harness = ModTestHarness.load("ae2", new Mod(), RESOURCES);
        assertTrue(harness.report().active(), harness.report().problems().toString());
        for (NodeKind kind : NodeKind.values()) {
            var block = harness.block("ae2:" + kind.id());
            var behaviors = block.behaviors(NodeBehavior.class);
            assertEquals(1, behaviors.size(), kind.id());
            assertEquals(kind, behaviors.get(0).kind());
            assertEquals(1, block.behaviors(BlockPlaceBehavior.class).size());
            assertEquals(1, block.behaviors(BlockRemoveBehavior.class).size());
            assertEquals(1, block.behaviors(BlockInteractBehavior.class).size());
        }
    }

    @Test
    void blockItemsPlaceTheirBlockAndHaveTextures() throws Exception {
        var harness = ModTestHarness.load("ae2", new Mod(), RESOURCES);
        for (NodeKind kind : NodeKind.values()) {
            var item = harness.item("ae2:" + kind.id());
            assertEquals("ae2:" + kind.id(),
                    item.components().get(PrismComponents.PLACES_BLOCK).orElseThrow().block().toString());
            assertTrue(Files.exists(RESOURCES.resolve("assets/ae2/textures/item/" + kind.id() + ".png")));
            assertTrue(Files.exists(RESOURCES.resolve("assets/ae2/textures/block/" + kind.id() + ".png")));
        }
    }

    @Test
    void cellsPatternsAndCrystalsLoad() {
        var harness = ModTestHarness.load("ae2", new Mod(), RESOURCES);
        for (var tier : com.aitchn.prism.validation.ae2.storage.StorageCell.Tier.values()) {
            harness.item(Ae2Content.cellKey(tier).toString());
        }
        harness.item("ae2:blank_pattern");
        harness.item("ae2:encoded_pattern");
        assertEquals(1600, harness.item("ae2:charged_certus_quartz_crystal").components()
                .get(Ae2Content.energy).orElseThrow().value());
    }
}
