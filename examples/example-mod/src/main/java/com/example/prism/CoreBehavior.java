package com.example.prism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.FormedStructure;
import com.aitchn.prism.api.block.StructureBehavior;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.Set;

/**
 * The ruby core is the controller of a small multiblock: a 3x3 floor of ruby blocks with the core on top in the
 * middle (see {@code prism/blocks/ruby_core.yml}). It lights up ({@code formed}) while the structure stands and goes
 * dark ({@code idle}) when a block of it is broken. What a formed structure does is the mod's; Prism only reports it.
 */
public record CoreBehavior() implements StructureBehavior {

    static CoreBehavior fromYaml(ConfigSection section) throws ContentException {
        section.rejectUnknownKeys(Set.of());
        return new CoreBehavior();
    }

    @Override
    public void onFormed(BlockContext context, FormedStructure structure) {
        Prism.blocks().setVariant(context.block(), "formed");
    }

    @Override
    public void onBroken(BlockContext context) {
        Prism.blocks().setVariant(context.block(), "idle");
    }
}
