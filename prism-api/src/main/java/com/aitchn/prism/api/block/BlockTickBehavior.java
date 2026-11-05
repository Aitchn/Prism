package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.jetbrains.annotations.NotNull;

/**
 * Capability: runs every {@link #tickInterval()} server ticks while the block's chunk is loaded, on the thread that
 * owns the region. Prism batches all ticking blocks of a chunk into one task, so thousands of ticking blocks cost one
 * task per chunk, not one per block. Keep each call short.
 */
public interface BlockTickBehavior extends Behavior {

    void onTick(@NotNull BlockContext context);

    /** Ticks between calls, at least 1. Read once when the block starts ticking. */
    default int tickInterval() {
        return 1;
    }
}
