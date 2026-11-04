package com.aitchn.prism.core.block;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * The 800 block states of a vanilla note block (16 instruments x 25 notes x powered), numbered 0 to 799. The
 * native-state block backend gives each custom block one of them. Index 0 (harp, note 0, unpowered) is what a player
 * gets from a freshly placed vanilla note block, so it is never allocated.
 */
public final class NoteBlockStates {

    /** Blockstate names of the instruments, in index order. */
    public static final List<String> INSTRUMENTS = List.of("harp", "basedrum", "snare", "hat", "bass", "flute", "bell",
            "guitar", "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling");

    public static final int NOTES = 25;
    public static final int COUNT = INSTRUMENTS.size() * NOTES * 2;

    /** One state: instrument blockstate name, note 0 to 24, powered. */
    public record State(@NotNull String instrument, int note, boolean powered) {
        /** The variant key used in a blockstates file. */
        public String variant() {
            return "instrument=" + instrument + ",note=" + note + ",powered=" + powered;
        }
    }

    private NoteBlockStates() {}

    public static @NotNull State state(int index) {
        if (index < 0 || index >= COUNT) {
            throw new IllegalArgumentException("note block state index out of range: " + index);
        }
        int powered = index % 2;
        int note = (index / 2) % NOTES;
        int instrument = index / (2 * NOTES);
        return new State(INSTRUMENTS.get(instrument), note, powered == 1);
    }

    /** @return the index of the state, or -1 if the instrument name is unknown or the note is out of range */
    public static int index(@NotNull String instrument, int note, boolean powered) {
        int i = INSTRUMENTS.indexOf(instrument);
        if (i < 0 || note < 0 || note >= NOTES) {
            return -1;
        }
        return i * NOTES * 2 + note * 2 + (powered ? 1 : 0);
    }
}
