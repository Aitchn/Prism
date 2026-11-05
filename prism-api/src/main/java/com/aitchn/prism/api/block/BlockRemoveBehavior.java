package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.jetbrains.annotations.NotNull;

/**
 * Capability: runs just before the block is removed, whatever the cause (player break, explosion, a mod's own
 * {@link BlockService#remove}). The block and its data still exist during the call; drop contents here. It cannot
 * be stopped; to keep a block from a player, use {@link BlockBreakBehavior}.
 */
public interface BlockRemoveBehavior extends Behavior {

    void onRemove(@NotNull BlockContext context);
}
