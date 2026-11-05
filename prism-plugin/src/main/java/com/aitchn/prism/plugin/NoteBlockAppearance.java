package com.aitchn.prism.plugin;

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
 * Custom blocks as vanilla note block states (no entity). The Java resource pack's {@code note_block} blockstates file
 * draws each allocated state with the look's model, and the Geyser block mappings draw the same state on Bedrock, so
 * this is the appearance both editions can see. Each look (variant x facing) owns one of 799 states. Ported from the
 * M5b native backend; identity no longer comes from the state but from {@link BlockIndex}.
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
        NoteBlock data = state(definition, facing, variant);
        if (data == null) {
            return false;
        }
        block.setBlockData(data, false);
        return true;
    }

    @Override
    public void update(Block block, BlockDefinition definition, BlockFace facing, String variant) {
        NoteBlock data = state(definition, facing, variant);
        if (data != null && block.getType() == Material.NOTE_BLOCK) {
            block.setBlockData(data, false);
        }
    }

    @Override
    public void despawn(Block block) {
        block.setType(Material.AIR, false);
    }

    @Override
    public boolean draws(Block block) {
        return block.getType() == Material.NOTE_BLOCK;
    }

    private NoteBlock state(BlockDefinition definition, BlockFace facing, String variant) {
        String id = new Appearances.Look(definition.key(), variant, facing.name()).id();
        OptionalInt index = allocator.allocate(id);
        if (index.isEmpty()) {
            log.severe("PRISM_NATIVE_OUT_OF_STATES cannot draw " + id + "; all 799 note block states are taken");
            return null;
        }
        NoteBlockStates.State state = NoteBlockStates.state(index.getAsInt());
        NoteBlock data = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        data.setInstrument(INSTRUMENTS[NoteBlockStates.INSTRUMENTS.indexOf(state.instrument())]);
        data.setNote(new Note(state.note()));
        data.setPowered(state.powered());
        return data;
    }
}
