package com.aitchn.prism.plugin;

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
 * Passable custom blocks ({@code prism:passable}) as tripwire states with {@code disarmed=true}: no collision, not
 * hiding their neighbours, drawn on Java by the pack's {@code tripwire} blockstates and on Bedrock by the Geyser
 * mapping. Each look owns one of {@link TripwireStates#COUNT} states. {@link NativeStateListener} keeps vanilla string
 * behaviour (connecting to neighbours, being pressed) from changing them.
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
        Tripwire data = state(definition, facing, variant);
        if (data == null) {
            return false;
        }
        block.setBlockData(data, false);
        return true;
    }

    @Override
    public void update(Block block, BlockDefinition definition, BlockFace facing, String variant) {
        Tripwire data = state(definition, facing, variant);
        if (data != null && block.getType() == Material.TRIPWIRE) {
            block.setBlockData(data, false);
        }
    }

    @Override
    public void despawn(Block block) {
        block.setType(Material.AIR, false);
    }

    @Override
    public boolean draws(Block block) {
        return block.getType() == Material.TRIPWIRE;
    }

    private Tripwire state(BlockDefinition definition, BlockFace facing, String variant) {
        String id = new Appearances.Look(definition.key(), variant, facing.name()).id();
        OptionalInt index = allocator.allocate(id);
        if (index.isEmpty()) {
            log.severe("PRISM_PASSABLE_OUT_OF_STATES cannot draw " + id + "; all " + TripwireStates.COUNT
                    + " tripwire states are taken");
            return null;
        }
        return data(index.getAsInt());
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
