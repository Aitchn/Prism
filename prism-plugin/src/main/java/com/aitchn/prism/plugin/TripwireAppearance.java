package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.core.block.StateAllocator;
import com.aitchn.prism.core.block.TripwireStates;
import java.util.OptionalInt;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Tripwire;

/**
 * Passable custom blocks ({@code prism:passable}) drawn as tripwire states with {@code disarmed=true}: no collision,
 * not hiding their neighbours, drawn on Java by the pack's {@code tripwire} blockstates and on Bedrock by the Geyser
 * mapping. The server stores the inert {@link Carrier#PASSABLE} block, so vanilla string behaviour (connecting to
 * neighbours, being pressed) has nothing to act on; viewers are shown the tripwire state ({@code BlockPacketRewriter}).
 * Each look owns one of {@link TripwireStates#COUNT} states.
 */
final class TripwireAppearance implements BlockAppearance {

    private final StateAllocator allocator;
    private final Logger log;

    TripwireAppearance(StateAllocator allocator, Logger log) {
        this.allocator = allocator;
        this.log = log;
    }

    @Override
    public boolean spawn(Block block, BlockDefinition definition, BlockFace facing, String variant) {
        if (lookData(definition, facing, variant) == null) {
            return false;
        }
        block.setType(Carrier.PASSABLE.material(), false);
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
        // A tripwire is the carrier of earlier versions, still there until its chunk is migrated.
        return block.getType() == Carrier.PASSABLE.material() || block.getType() == Material.TRIPWIRE;
    }

    /** The tripwire state viewers see for this look, allocating it on first use; null if all states are taken. */
    Tripwire lookData(BlockDefinition definition, BlockFace facing, String variant) {
        String id = new Appearances.Look(definition.key(), variant, facing.name()).id();
        OptionalInt index = allocator.allocate(id);
        if (index.isEmpty()) {
            log.severe("PRISM_PASSABLE_OUT_OF_STATES cannot draw " + id + "; all " + TripwireStates.COUNT
                    + " tripwire states are taken");
            return null;
        }
        return data(index.getAsInt());
    }

    /** The state already allocated to this look, or null; never allocates. Works for blocks of absent mods. */
    Tripwire existingState(PrismKey key, BlockFace facing, String variant) {
        OptionalInt index = allocator.indexOf(new Appearances.Look(key, variant, facing.name()).id());
        return index.isEmpty() ? null : data(index.getAsInt());
    }

    /** The block data of tripwire state {@code id}. */
    static Tripwire data(int id) {
        TripwireStates.State state = TripwireStates.state(id);
        Tripwire data = (Tripwire) Material.TRIPWIRE.createBlockData();
        data.setDisarmed(true);
        data.setAttached(state.attached());
        data.setPowered(state.powered());
        data.setFace(BlockFace.EAST, state.east());
        data.setFace(BlockFace.NORTH, state.north());
        data.setFace(BlockFace.SOUTH, state.south());
        data.setFace(BlockFace.WEST, state.west());
        return data;
    }
}
