package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.jetbrains.annotations.NotNull;

/**
 * Capability: runs when the chunk holding the block has loaded (including chunks already loaded when Prism or the
 * mod enabled) and before it unloads. Use it to rebuild in-memory state such as network membership after a restart.
 * Per-block data is available in both calls and is saved after {@link #onUnload}.
 */
public interface BlockLoadBehavior extends Behavior {

    void onLoad(@NotNull BlockContext context);

    default void onUnload(@NotNull BlockContext context) {}
}
