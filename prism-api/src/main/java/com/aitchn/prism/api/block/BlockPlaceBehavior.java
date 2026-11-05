package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.jetbrains.annotations.NotNull;

/**
 * Capability: reacts after a block of this definition was placed by a player placing its item. Use it to start
 * ticking, register the block in a network or create per-block data. {@link BlockContext#player()} is the placer.
 * Blocks placed through {@link BlockService#place} by a mod do not trigger it; the caller already knows.
 */
public interface BlockPlaceBehavior extends Behavior {

    void onPlace(@NotNull BlockContext context);
}
