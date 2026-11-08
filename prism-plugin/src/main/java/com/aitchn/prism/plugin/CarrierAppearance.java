package com.aitchn.prism.plugin;

import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.core.block.Appearances;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Custom blocks whose look is a client-side state (a borrowed vanilla state, or an invisible shell plus the display
 * layer). The server stores the inert {@link Carrier}; viewers are shown the look by the packet rewriter, so nothing in
 * the world changes when a look does. Identity comes from {@link BlockIndex}, never from the world (ADR-0022).
 */
final class CarrierAppearance implements BlockAppearance {

    private final Carrier carrier;
    private final Supplier<ClientLooks> looks;
    private final Logger log;

    CarrierAppearance(Carrier carrier, Supplier<ClientLooks> looks, Logger log) {
        this.carrier = carrier;
        this.looks = looks;
        this.log = log;
    }

    Carrier carrier() {
        return carrier;
    }

    @Override
    public boolean spawn(Block block, BlockDefinition definition, BlockFace facing, String variant) {
        String id = new Appearances.Look(definition.key(), variant, facing.name()).id();
        if (looks.get().lookFor(carrier, id) == null) {
            log.severe("PRISM_LOOK_UNPLACED cannot draw " + id + ": no client representation was allocated for it");
            return false;
        }
        block.setType(carrier.material(), false);
        return true;
    }

    /** Nothing to do in the world: the carrier never changes. The new look is published by the block service. */
    @Override
    public void update(Block block, BlockDefinition definition, BlockFace facing, String variant) {}

    @Override
    public void despawn(Block block) {
        block.setType(Material.AIR, false);
    }

    @Override
    public boolean draws(Block block) {
        // The note block and tripwire are the carriers of earlier versions, still there until their chunk is migrated.
        return block.getType() == carrier.material() || Carrier.legacy(block.getType()) == carrier;
    }
}
