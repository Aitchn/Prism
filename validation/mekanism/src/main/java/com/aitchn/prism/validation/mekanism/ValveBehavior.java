package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockLoadBehavior;
import com.aitchn.prism.api.block.BlockRemoveBehavior;
import com.aitchn.prism.api.block.BlockTickBehavior;
import com.aitchn.prism.api.block.FormedStructure;
import com.aitchn.prism.api.block.StructureBehavior;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * The valve of a dynamic tank. It carries {@code prism:cuboid}, so Prism finds the box (walls of tank blocks, valves and
 * structural glass, 3 to 18 blocks a side, empty inside) and calls {@link #onFormed} and {@link #onBroken}. Pipes
 * connect to valves and reach the tank of the box. The plain tank blocks and glass have no behavior of their own.
 *
 * <pre>
 * behaviors:
 *   - type: mekanism:valve
 * </pre>
 */
public record ValveBehavior()
        implements StructureBehavior, BlockLoadBehavior, BlockRemoveBehavior, BlockTickBehavior {

    static ValveBehavior fromYaml(ConfigSection section) throws ContentException {
        section.rejectUnknownKeys(Set.of());
        return new ValveBehavior();
    }

    @Override
    public void onFormed(@NotNull BlockContext context, @NotNull FormedStructure structure) {
        Grid.get().attachValve(context.custom(), structure);
    }

    @Override
    public void onBroken(@NotNull BlockContext context) {
        Grid.get().detachValve(context.custom(), false);
    }

    @Override
    public void onLoad(@NotNull BlockContext context) {
        Grid.get().loadValve(context.custom());
    }

    @Override
    public void onUnload(@NotNull BlockContext context) {
        Grid.get().detachValve(context.custom(), true);
    }

    @Override
    public void onRemove(@NotNull BlockContext context) {
        Grid.get().detachValve(context.custom(), false);
    }

    /** Looks at what Prism reports (a box formed before a restart is not announced again) and saves the content. */
    @Override
    public void onTick(@NotNull BlockContext context) {
        Grid.get().tickValve(context.custom());
    }

    @Override
    public int tickInterval() {
        return 20;
    }
}
