package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.core.block.Appearances;
import com.aitchn.prism.core.block.NoteBlockStates;
import com.aitchn.prism.core.block.StateAllocator;
import java.util.OptionalInt;
import java.util.logging.Logger;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.NoteBlock;

/**
 * Custom blocks drawn as vanilla note block states (no entity). The server stores the inert {@link Carrier#SOLID}
 * block; viewers are shown the note block state of the look ({@code BlockPacketRewriter}), which the Java resource
 * pack's {@code note_block} blockstates file draws with the look's model and the Geyser block mappings draw on Bedrock.
 * Each look (variant x facing) owns one of 799 states. Identity comes from {@link BlockIndex}, never from the world.
 */
final class NoteBlockAppearance implements BlockAppearance {

    private static final Instrument[] INSTRUMENTS = {
        Instrument.PIANO, Instrument.BASS_DRUM, Instrument.SNARE_DRUM, Instrument.STICKS, Instrument.BASS_GUITAR,
        Instrument.FLUTE, Instrument.BELL, Instrument.GUITAR, Instrument.CHIME, Instrument.XYLOPHONE,
        Instrument.IRON_XYLOPHONE, Instrument.COW_BELL, Instrument.DIDGERIDOO, Instrument.BIT, Instrument.BANJO,
        Instrument.PLING
    };

    private final StateAllocator allocator;
    private final Logger log;

    NoteBlockAppearance(StateAllocator allocator, Logger log) {
        this.allocator = allocator;
        this.log = log;
    }

    @Override
    public boolean spawn(Block block, BlockDefinition definition, BlockFace facing, String variant) {
        if (lookData(definition, facing, variant) == null) {
            return false;
        }
        block.setType(Carrier.SOLID.material(), false);
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
        // A note block is the carrier of earlier versions, still there until its chunk is migrated.
        return block.getType() == Carrier.SOLID.material() || block.getType() == Material.NOTE_BLOCK;
    }

    /** The state already allocated to this look, or null; never allocates (for looks shown to Bedrock only). */
    NoteBlock existingState(BlockDefinition definition, BlockFace facing, String variant) {
        return existingState(definition.key(), facing, variant);
    }

    /** The state already allocated to this look, or null; never allocates. Works for blocks of absent mods. */
    NoteBlock existingState(PrismKey key, BlockFace facing, String variant) {
        OptionalInt index = allocator.indexOf(new Appearances.Look(key, variant, facing.name()).id());
        return index.isEmpty() ? null : data(index.getAsInt());
    }

    /** The note block state viewers see for this look, allocating it on first use; null if all states are taken. */
    NoteBlock lookData(BlockDefinition definition, BlockFace facing, String variant) {
        String id = new Appearances.Look(definition.key(), variant, facing.name()).id();
        OptionalInt index = allocator.allocate(id);
        if (index.isEmpty()) {
            log.severe("PRISM_NATIVE_OUT_OF_STATES cannot draw " + id + "; all 799 note block states are taken");
            return null;
        }
        return data(index.getAsInt());
    }

    private static NoteBlock data(int index) {
        NoteBlockStates.State state = NoteBlockStates.state(index);
        NoteBlock data = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        data.setInstrument(INSTRUMENTS[NoteBlockStates.INSTRUMENTS.indexOf(state.instrument())]);
        data.setNote(new Note(state.note()));
        data.setPowered(state.powered());
        return data;
    }
}
