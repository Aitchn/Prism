package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockCapability;
import com.aitchn.prism.validation.mekanism.logic.ChemicalPorts;
import com.aitchn.prism.validation.mekanism.logic.EnergyBuffer;

/**
 * What Mekanism blocks offer each other. Prism only carries the lookup ({@code Prism.blocks().capability}); these
 * types and their meaning (joules, millibuckets) are this mod's design, so another mod can use them only by depending on
 * this one, or by agreeing on the same key and interface.
 */
public final class MekanismCapabilities {

    /** An energy buffer in joules. */
    public static final BlockCapability<EnergyBuffer> ENERGY =
            BlockCapability.of(new PrismKey("mekanism", "energy"), EnergyBuffer.class);

    /** The chemical tanks pipes may fill and drain. */
    public static final BlockCapability<ChemicalPorts> CHEMICAL =
            BlockCapability.of(new PrismKey("mekanism", "chemical"), ChemicalPorts.class);

    private MekanismCapabilities() {}
}
