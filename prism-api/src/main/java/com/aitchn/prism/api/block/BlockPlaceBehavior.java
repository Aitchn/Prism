package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.jetbrains.annotations.NotNull;

/**
 * Capability: runs after the block has been placed through {@link BlockService#place}. Called on the thread that
 * owns the block's region. {@link BlockContext#player()} is null because {@code place} has no player; the block can
 * no longer be refused here.
 */
public interface BlockPlaceBehavior extends Behavior {
    void onPlace(@NotNull BlockContext context);
}
