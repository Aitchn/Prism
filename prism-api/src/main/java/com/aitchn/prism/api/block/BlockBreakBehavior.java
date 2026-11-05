package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.jetbrains.annotations.NotNull;

/**
 * Capability: runs when a player finished breaking the block, before it is removed; {@link BlockContext#cancel()}
 * keeps it. {@link BlockRemoveBehavior} runs afterwards for every removal, including explosions.
 */
public interface BlockBreakBehavior extends Behavior {

    void onBreak(@NotNull BlockContext context);
}
