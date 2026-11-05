package com.aitchn.validation.botania;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockCapability;
import com.aitchn.validation.botania.logic.ManaCollector;

/** What Botania blocks offer their neighbours. Mana is this mod's own concept: Prism only does the lookup. */
public final class ManaCapabilities {

    public static final BlockCapability<ManaCollector> MANA =
            BlockCapability.of(new PrismKey("botania", "mana"), ManaCollector.class);

    private ManaCapabilities() {}
}
