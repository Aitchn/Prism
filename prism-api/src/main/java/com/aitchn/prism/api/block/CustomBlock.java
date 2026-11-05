package com.aitchn.prism.api.block;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * A custom block that exists in a loaded chunk. Use it only on the thread that owns the block's region, and do not
 * keep it after the block is removed or its chunk unloads (look it up again with {@link BlockService#get}).
 */
public interface CustomBlock {

    @NotNull Block block();

    @NotNull PrismKey key();

    @NotNull BlockDefinition definition();

    /**
     * The direction the block faces. {@link BlockFace#NORTH} unless the definition has {@code prism:rotation}; then it
     * is chosen when the block is placed (see {@link com.aitchn.prism.api.content.PrismComponents#ROTATION}).
     */
    @NotNull BlockFace facing();

    /**
     * Data private to this block, like a vanilla block entity. It is saved with the chunk and deleted when the block
     * is removed. Use your own namespace for keys. The container is live: changes are kept without a save call.
     */
    @NotNull PersistentDataContainer data();
}
