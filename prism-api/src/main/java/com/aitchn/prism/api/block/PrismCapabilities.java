package com.aitchn.prism.api.block;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.item.ItemHandler;
import org.bukkit.inventory.Inventory;

/** Capabilities Prism itself answers. They describe vanilla concepts only. */
public final class PrismCapabilities {

    /**
     * An item inventory. Prism answers it for vanilla containers (chests, furnaces, hoppers, ...); custom blocks may
     * answer it through {@link BlockCapabilityProvider}, for example with an inventory kept in their per-block data.
     */
    public static final BlockCapability<Inventory> INVENTORY =
            BlockCapability.of(new PrismKey(PrismKey.PRISM_NAMESPACE, "inventory"), Inventory.class);

    /**
     * Slotless item transfer ({@link ItemHandler}). Prism answers it for vanilla containers and for custom blocks that
     * offer {@link #INVENTORY}; a custom block can also answer it directly (a storage network, a machine with separate
     * input and output buffers per side).
     */
    public static final BlockCapability<ItemHandler> ITEM_HANDLER =
            BlockCapability.of(new PrismKey(PrismKey.PRISM_NAMESPACE, "item_handler"), ItemHandler.class);

    private PrismCapabilities() {}
}
