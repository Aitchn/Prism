package com.aitchn.prism.api.item;

import com.aitchn.prism.api.content.ItemDefinition;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** What a use (right click) of a custom item looks like to a behavior. Valid only during the call. */
public interface ItemUseContext {

    @NotNull Player player();

    @NotNull ItemStack stack();

    @NotNull ItemDefinition definition();

    @NotNull EquipmentSlot hand();

    /** Stops the vanilla action of the carrier item (for example placing a block or eating). */
    void cancelVanillaAction();
}
