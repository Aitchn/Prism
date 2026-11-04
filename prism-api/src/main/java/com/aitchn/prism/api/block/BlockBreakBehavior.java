package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.jetbrains.annotations.NotNull;

/** Capability: runs before the block is broken by a player; {@link BlockContext#cancel()} keeps it. */
public interface BlockBreakBehavior extends Behavior {

    void onBreak(@NotNull BlockContext context);
}
