package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockLoadBehavior;
import com.aitchn.prism.api.block.BlockPlaceBehavior;
import com.aitchn.prism.api.block.BlockRemoveBehavior;
import com.aitchn.prism.api.block.BlockTickBehavior;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * A casing block of a dynamic tank: tank wall, structural glass, or (with {@code valve: true}) a valve pipes connect to.
 * The structure is found again whenever a member appears or goes. It has no interact behavior on purpose, so players can
 * still place blocks against it; the Energy Tablet reads the structure out.
 *
 * <pre>
 * behaviors:
 *   - type: mekanism:casing
 *     valve: false
 * </pre>
 */
public record CasingBehavior(boolean valve)
        implements BlockPlaceBehavior, BlockLoadBehavior, BlockRemoveBehavior, BlockTickBehavior {

    static CasingBehavior fromYaml(ConfigSection section) throws ContentException {
        section.rejectUnknownKeys(Set.of("valve"));
        return new CasingBehavior(section.getBoolean("valve", false));
    }

    @Override
    public void onPlace(@NotNull BlockContext context) {
        Grid.get().addMember(context.custom(), valve);
    }

    @Override
    public void onLoad(@NotNull BlockContext context) {
        Grid.get().addMember(context.custom(), valve);
    }

    @Override
    public void onUnload(@NotNull BlockContext context) {
        Grid.get().removeMember(context.custom(), true);
    }

    @Override
    public void onRemove(@NotNull BlockContext context) {
        Grid.get().removeMember(context.custom(), false);
    }

    @Override
    public void onTick(@NotNull BlockContext context) {
        Grid.get().tickMember(context.custom());
    }

    /** Only the structure's anchor saves anything, and only every five seconds. */
    @Override
    public int tickInterval() {
        return 100;
    }
}
