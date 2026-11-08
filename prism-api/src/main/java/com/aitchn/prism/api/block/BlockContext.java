package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.BlockDefinition;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A block event as seen by a behavior. Valid only during the call, on the thread that owns the block's region. */
public interface BlockContext {

    @NotNull Block block();

    @NotNull BlockDefinition definition();

    /** The custom block with its facing and per-block data. */
    @NotNull CustomBlock custom();

    /** The player involved, or null for non-player causes (explosions, ticks, a mod's own call). */
    @Nullable Player player();

    /** The face the player clicked, for interactions and placement; null otherwise. */
    @Nullable BlockFace face();

    /** Where the click hit, relative to the block's minimum corner (0..1 on each axis); null if unknown. */
    @Nullable Vector hitPosition();

    /** The hand the player used; null when no player or hand is involved. */
    @Nullable EquipmentSlot hand();

    /** The stack in {@link #hand()}; null when no player is involved, an empty stack when the hand is empty. */
    @Nullable ItemStack heldItem();

    /**
     * For {@link BlockPlaceBehavior}: a copy (amount 1) of the stack the block was placed from, with its per-stack
     * data, so a block can take back what {@link BlockDropBehavior} stored. Null when the block was placed by code or
     * outside of placement.
     */
    default @Nullable ItemStack placedFrom() {
        return null;
    }

    /** Stops the default outcome (opening nothing, breaking nothing, ...). Ignored where nothing can be stopped. */
    void cancel();

    boolean isCancelled();

    /**
     * For {@link BlockInteractBehavior}: this behavior did not use the click, so the vanilla outcome goes ahead (the
     * held item is used or placed against the block). A click counts as handled when any interact behavior runs
     * without calling this, or when {@link #cancel()} is called.
     */
    default void pass() {}
}
