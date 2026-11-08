package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

/**
 * Capability: decides which neighbours a connected block ({@code prism:connections}) reaches, in place of the
 * component's {@code connects_to} rules. Called on the block's thread when the block or a neighbour is placed or
 * removed, and on {@link BlockService#refreshConnections}; {@code side} points from this block to {@code neighbor}.
 * Only sides listed in the component's {@code faces} are asked. Keep it cheap: no world changes, no chunk loads.
 */
public interface BlockConnectionBehavior extends Behavior {

    boolean connects(@NotNull CustomBlock self, @NotNull BlockFace side, @NotNull Block neighbor);
}
