package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.jetbrains.annotations.NotNull;

/**
 * Capability: runs before the block is broken by a player, which can cancel it with {@link BlockContext#cancel()},
 * or when an explosion destroys it ({@link BlockContext#player()} is null and cancelling has no effect).
 */
public interface BlockBreakBehavior extends Behavior {

    void onBreak(@NotNull BlockContext context);
}
