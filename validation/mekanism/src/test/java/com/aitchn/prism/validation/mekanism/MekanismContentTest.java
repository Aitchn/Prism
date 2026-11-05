package com.aitchn.prism.validation.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.testkit.ModTestHarness;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The YAML content of the validation mod loads through the real Prism lifecycle. */
class MekanismContentTest {

    private static final class Mod implements PrismMod {
        @Override
        public void onInitialize(ModContext context) {
            context.registerBehavior("block", MekanismBlock.class, MekanismBlock::fromYaml);
        }
    }

    @Test
    void allBlocksAndItemsLoad() {
        var harness = ModTestHarness.load("mekanism", new Mod(), Path.of("src/main/resources"));
        assertTrue(harness.report().active(), harness.report().problems().toString());
        assertEquals(9, harness.manager().blocks().size());
    }

    @Test
    void machineBlockCarriesItsMachineIdFromYaml() {
        var harness = ModTestHarness.load("mekanism", new Mod(), Path.of("src/main/resources"));
        BlockDefinition block = harness.block("mekanism:enrichment_chamber");
        MekanismBlock behavior = (MekanismBlock) block.behaviors(BlockInteractBehavior.class).get(0);
        assertEquals(MekanismBlock.Kind.MACHINE, behavior.kind());
        assertEquals("enrichment_chamber", behavior.machine());
        assertTrue(harness.item("mekanism:enrichment_chamber").components().has(PrismComponents.PLACES_BLOCK));
    }
}
