package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockLoadBehavior;
import com.aitchn.prism.api.block.BlockPlaceBehavior;
import com.aitchn.prism.api.block.BlockRemoveBehavior;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * A universal cable: joins the generators and machines touching it into one energy network. It has no interact
 * behavior on purpose, so players can still place blocks against it; the Energy Tablet reads a network out.
 */
public record CableBehavior() implements BlockPlaceBehavior, BlockLoadBehavior, BlockRemoveBehavior {

    static CableBehavior fromYaml(ConfigSection section) throws ContentException {
        section.rejectUnknownKeys(Set.of());
        return new CableBehavior();
    }

    @Override
    public void onPlace(@NotNull BlockContext context) {
        Grid.get().addConduit(context.block(), true);
    }

    @Override
    public void onLoad(@NotNull BlockContext context) {
        Grid.get().addConduit(context.block(), true);
    }

    @Override
    public void onUnload(@NotNull BlockContext context) {
        Grid.get().removeConduit(context.block(), true);
    }

    @Override
    public void onRemove(@NotNull BlockContext context) {
        Grid.get().removeConduit(context.block(), true);
    }
}
