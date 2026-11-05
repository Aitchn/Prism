package com.aitchn.prism.api.item;

import com.aitchn.prism.api.content.ItemDefinition;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** What a use (right click) of a custom item looks like to a behavior. Valid only during the call. */
public interface ItemUseContext {

    @NotNull Player player();

    @NotNull ItemStack stack();

    @NotNull ItemDefinition definition();

    @NotNull EquipmentSlot hand();

    /** The block the item was used on, or null when used in the air. May be a custom block. */
    @Nullable Block clickedBlock();

    /** The clicked face of {@link #clickedBlock()}, or null when used in the air. */
    @Nullable BlockFace clickedFace();

    /** Stops the vanilla action of the carrier item (for example placing a block or eating). */
    void cancelVanillaAction();
}
