package com.aitchn.prism.plugin;

import com.aitchn.prism.api.content.BlockDefinition;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/** How a custom block is drawn. Identity and data live in {@link BlockIndex}; this only sets what players see. */
interface BlockAppearance {

    /** Sets the carrier and whatever draws the block. The spot is free. Returns false if this look cannot be shown. */
    boolean spawn(Block block, BlockDefinition definition, BlockFace facing, String variant);

    /** Switches an existing block to another look. */
    void update(Block block, BlockDefinition definition, BlockFace facing, String variant);

    /** Removes what draws the block and sets the carrier to air. */
    void despawn(Block block);

    /**
     * The carrier was replaced by something outside Prism (the world wins): drops what drew the block but leaves the
     * block that is there now alone. For a look that lives only in packets there is nothing to drop.
     */
    default void orphaned(Block block) {}

    /** Whether {@code block}'s carrier belongs to this appearance. */
    boolean draws(Block block);
}
