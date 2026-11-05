package com.aitchn.prism.api.block;

import com.aitchn.prism.api.PrismKey;
import org.bukkit.inventory.Inventory;

/** Capabilities Prism itself answers. They describe vanilla concepts only. */
public final class PrismCapabilities {

    /**
     * An item inventory. Prism answers it for vanilla containers (chests, furnaces, hoppers, ...); custom blocks may
     * answer it through {@link BlockCapabilityProvider}, for example with an inventory kept in their per-block data.
     */
    public static final BlockCapability<Inventory> INVENTORY =
            BlockCapability.of(new PrismKey(PrismKey.PRISM_NAMESPACE, "inventory"), Inventory.class);

    private PrismCapabilities() {}
}
