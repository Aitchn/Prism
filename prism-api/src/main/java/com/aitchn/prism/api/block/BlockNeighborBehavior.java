package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

/**
 * Capability: runs when one of the six adjacent blocks was placed or removed, by a player or through
 * {@link BlockService}. {@code side} points from this block to the neighbour. Changes made by other plugins writing
 * blocks directly (for example WorldEdit) are not reported.
 */
public interface BlockNeighborBehavior extends Behavior {

    void onNeighborChanged(@NotNull BlockContext context, @NotNull BlockFace side);
}
