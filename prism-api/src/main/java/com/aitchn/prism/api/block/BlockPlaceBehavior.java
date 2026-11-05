package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.jetbrains.annotations.NotNull;

/**
 * Capability: runs right after the block was placed, by a player using its item or by a mod through
 * {@link BlockService#place}. {@link BlockContext#player()} is the placer, or null when a mod placed it without one.
 * The block, its facing and its (empty) data already exist. Use it to initialise per-block data or join a network.
 */
public interface BlockPlaceBehavior extends Behavior {

    void onPlace(@NotNull BlockContext context);
}
