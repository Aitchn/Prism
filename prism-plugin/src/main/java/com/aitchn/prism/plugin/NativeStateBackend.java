package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockService;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.core.block.NoteBlockStates;
import com.aitchn.prism.core.block.StateAllocator;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.logging.Logger;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.jetbrains.annotations.NotNull;

/**
 * Custom blocks as vanilla note block states: no entity, no per-chunk data, and the block is drawn by the resource
 * pack's {@code note_block} blockstates file. Each custom block owns one of 799 states ({@link StateAllocator}).
 * Trade-offs: a player-made note block that happens to have an allocated state is read as that custom block, the
 * 'carrier' of a definition is ignored, and BetterModel-driven blocks are not supported by this backend.
 */
final class NativeStateBackend implements BlockService {

    private static final Instrument[] INSTRUMENTS = {
        Instrument.PIANO, Instrument.BASS_DRUM, Instrument.SNARE_DRUM, Instrument.STICKS, Instrument.BASS_GUITAR,
        Instrument.FLUTE, Instrument.BELL, Instrument.GUITAR, Instrument.CHIME, Instrument.XYLOPHONE,
        Instrument.IRON_XYLOPHONE, Instrument.COW_BELL, Instrument.DIDGERIDOO, Instrument.BIT, Instrument.BANJO,
        Instrument.PLING
    };

    private final Registry<BlockDefinition> definitions;
    private final ItemService items;
    private final StateAllocator allocator;
    private final Logger log;

    NativeStateBackend(Registry<BlockDefinition> definitions, ItemService items, StateAllocator allocator, Logger log) {
        this.definitions = definitions;
        this.items = items;
        this.allocator = allocator;
        this.log = log;
    }

    @Override
    public boolean place(@NotNull PrismKey key, @NotNull Location location) {
        Optional<BlockDefinition> found = definitions.get(key);
        if (found.isEmpty()) {
            return false;
        }
        if (ModelAnchors.modelOf(found.get()).isPresent()) {
            log.warning("PRISM_NATIVE_UNSUPPORTED " + key + " uses a BetterModel model; the native backend cannot draw it");
            return false;
        }
        Block block = location.getBlock();
        if (!(block.isEmpty() || block.isReplaceable()) || identify(block).isPresent()) {
            return false;
        }
        OptionalInt index = allocator.allocate(key);
        if (index.isEmpty()) {
            log.severe("PRISM_NATIVE_OUT_OF_STATES cannot place " + key);
            return false;
        }
        NoteBlockStates.State state = NoteBlockStates.state(index.getAsInt());
        NoteBlock data = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        data.setInstrument(INSTRUMENTS[NoteBlockStates.INSTRUMENTS.indexOf(state.instrument())]);
        data.setNote(new Note(state.note()));
        data.setPowered(state.powered());
        block.setBlockData(data, false);
        return true;
    }

    @Override
    public @NotNull Optional<PrismKey> identify(@NotNull Block block) {
        if (block.getType() != Material.NOTE_BLOCK || !(block.getBlockData() instanceof NoteBlock data)) {
            return Optional.empty();
        }
        String instrument = null;
        for (int i = 0; i < INSTRUMENTS.length; i++) {
            if (INSTRUMENTS[i] == data.getInstrument()) {
                instrument = NoteBlockStates.INSTRUMENTS.get(i);
                break;
            }
        }
        if (instrument == null) {
            return Optional.empty();
        }
        int index = NoteBlockStates.index(instrument, data.getNote().getId(), data.isPowered());
        return index < 0 ? Optional.empty() : allocator.keyFor(index);
    }

    @Override
    public boolean remove(@NotNull Block block, boolean dropItem) {
        Optional<PrismKey> key = identify(block);
        if (key.isEmpty()) {
            return false;
        }
        block.setType(Material.AIR, false);
        if (dropItem) {
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            definitions.get(key.get()).flatMap(BlockDefinition::drop)
                    .flatMap(drop -> items.create(drop, 1))
                    .ifPresent(stack -> block.getWorld().dropItemNaturally(center, stack));
        }
        return true;
    }
}
