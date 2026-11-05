package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Capability: fills the item a block drops from the block's own data, so the block can be picked up with what it holds
 * (charge, contents, settings). Runs before {@link BlockRemoveBehavior} while the data still exists, only when the
 * block drops its item (not in creative). Write into the stack with {@code Prism.items().editData(drop, ...)} so its
 * lore shows what it carries; read it back in {@link BlockPlaceBehavior} through {@link BlockContext#placedFrom()}.
 */
public interface BlockDropBehavior extends Behavior {

    void onDrop(@NotNull BlockContext context, @NotNull ItemStack drop);
}
