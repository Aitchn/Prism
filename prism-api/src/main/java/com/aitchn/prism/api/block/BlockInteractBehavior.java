package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.jetbrains.annotations.NotNull;

/** Capability: runs when a player right-clicks the block. Called on the thread that owns the block's region. */
public interface BlockInteractBehavior extends Behavior {

    void onInteract(@NotNull BlockContext context);
}
