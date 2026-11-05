package com.aitchn.validation.botania;

import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.block.BlockLoadBehavior;
import com.aitchn.prism.api.block.BlockPlaceBehavior;
import com.aitchn.prism.api.block.BlockRemoveBehavior;
import com.aitchn.prism.api.block.BlockTickBehavior;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.Set;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/**
 * The one behavior all stateful Botania blocks share. {@code kind} in the block YAML selects the block entity:
 * {@code pool}, {@code spreader}, {@code apothecary} or {@code flower:<id>}. Prism calls it on the right region thread;
 * its state is saved in the block's own data.
 */
record BlockEntityBehavior(Supplier<BotaniaRuntime> runtime, String kind)
        implements BlockPlaceBehavior, BlockLoadBehavior, BlockTickBehavior, BlockRemoveBehavior,
        BlockInteractBehavior {

    static BlockEntityBehavior fromYaml(Supplier<BotaniaRuntime> runtime, ConfigSection section)
            throws ContentException {
        section.rejectUnknownKeys(Set.of("kind"));
        return new BlockEntityBehavior(runtime, section.requireString("kind"));
    }

    @Override
    public void onPlace(@NotNull BlockContext context) {
        runtime.get().ensure(context.custom(), kind);
    }

    @Override
    public void onLoad(@NotNull BlockContext context) {
        runtime.get().ensure(context.custom(), kind);
    }

    @Override
    public void onUnload(@NotNull BlockContext context) {
        runtime.get().unloaded(context.custom());
    }

    @Override
    public void onTick(@NotNull BlockContext context) {
        runtime.get().tick(context.custom(), kind);
    }

    @Override
    public void onRemove(@NotNull BlockContext context) {
        runtime.get().removed(context.custom());
    }

    @Override
    public void onInteract(@NotNull BlockContext context) {
        runtime.get().interact(context, kind);
    }
}
