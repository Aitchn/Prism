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
 * A pressurized tube: joins the machines touching it into one chemical network. It has no interact
 * behavior on purpose, so players can still place blocks against it; the Energy Tablet reads a network out.
 */
public record PipeBehavior(int tier) implements BlockPlaceBehavior, BlockLoadBehavior, BlockRemoveBehavior {

    static PipeBehavior fromYaml(ConfigSection section) throws ContentException {
        section.rejectUnknownKeys(Set.of("tier"));
        int tier = section.getInt("tier").orElse(0);
        if (tier < 0 || tier > 3) {
            throw section.error("tier must be 0 to 3");
        }
        return new PipeBehavior(tier);
    }

    @Override
    public void onPlace(@NotNull BlockContext context) {
        Grid.get().addConduit(context.block(), Grid.Kind.PIPE, tier);
    }

    @Override
    public void onLoad(@NotNull BlockContext context) {
        Grid.get().addConduit(context.block(), Grid.Kind.PIPE, tier);
    }

    @Override
    public void onUnload(@NotNull BlockContext context) {
        Grid.get().removeConduit(context.block(), Grid.Kind.PIPE);
    }

    @Override
    public void onRemove(@NotNull BlockContext context) {
        Grid.get().removeConduit(context.block(), Grid.Kind.PIPE);
    }
}
