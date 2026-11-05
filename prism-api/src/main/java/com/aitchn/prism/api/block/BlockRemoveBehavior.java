package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.jetbrains.annotations.NotNull;

/**
 * Capability: runs just before {@link BlockService#remove} takes the block away, whatever the cause (player break,
 * explosion, a mod's own call). The block is still in the world during the call. Called on the thread that owns the
 * block's region.
 */
public interface BlockRemoveBehavior extends Behavior {
    void onRemove(@NotNull BlockContext context);
}
