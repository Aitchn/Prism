package com.aitchn.prism.client;

import com.aitchn.prism.core.block.ChunkLooks;
import com.aitchn.prism.core.block.PalettedSection;
import com.aitchn.prism.core.block.SectionStates;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.MapPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BitStorage;
import java.util.function.IntPredicate;

/**
 * Chunk sections for Geyser viewers. A synthetic state id is larger than any state of the server version, so the packet
 * library's own palette code cannot hold it (a global palette has a fixed width there, and a state it does not know
 * decodes as air): the section is read into plain ids, rewritten, and laid out again by {@link PalettedSection} with the
 * palette that fits, then put back into the same {@link DataPalette} object so the rest of the packet is untouched.
 */
final class BedrockSections {

    private BedrockSections() {}

    /**
     * Shows a Geyser viewer the synthetic (or, lacking one, the Java) look of every custom block in the section, and
     * brings a global palette to the width Geyser reads.
     *
     * @return the number of blocks changed, plus one if only the palette width had to change
     */
    static int rewrite(Chunk_v1_18 section, ChunkLooks looks, int sectionY, int globalBits, IntPredicate legacy,
            ChunkLooks.ForeignSink foreign) {
        int[] states = read(section);
        int changed = looks.rewriteSection(sectionY, new SectionStates() {
            @Override
            public int get(int x, int y, int z) {
                return states[PalettedSection.index(x, y, z)];
            }

            @Override
            public void set(int x, int y, int z, int state) {
                states[PalettedSection.index(x, y, z)] = state;
            }
        }, legacy, foreign, true);
        if (changed == 0) {
            return normalizeWidth(section, globalBits) ? 1 : 0;
        }
        install(section.getChunkData(), PalettedSection.encode(states, globalBits));
        return changed;
    }

    /**
     * Writes a section that uses the global palette again at exactly {@code globalBits} bits per block when it was
     * written at another width. Geyser derives the width of the global palette from its registry size, which the
     * synthetic states made larger than the server's; vanilla clients agree with the server because both use the
     * vanilla size, Geyser does not.
     *
     * @return whether the section changed
     */
    static boolean normalizeWidth(Chunk_v1_18 section, int globalBits) {
        DataPalette data = section.getChunkData();
        if (globalBits <= 0 || !(data.palette instanceof GlobalPalette) || data.storage == null
                || data.storage.getBitsPerEntry() == globalBits) {
            return false;
        }
        install(data, PalettedSection.encode(read(section), globalBits));
        return true;
    }

    /** Every block state id of the section, indexed by {@link PalettedSection#index}. */
    static int[] read(Chunk_v1_18 section) {
        int[] states = new int[PalettedSection.SIZE];
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    states[PalettedSection.index(x, y, z)] = section.getBlockId(x, y, z);
                }
            }
        }
        return states;
    }

    /** Replaces the palette and storage of {@code target} with {@code encoded}. */
    static void install(DataPalette target, PalettedSection.Encoded encoded) {
        switch (encoded.kind()) {
            case SINGLETON -> {
                target.palette = new SingletonPalette(encoded.palette()[0]);
                target.storage = null;
            }
            case LIST -> {
                target.palette = new ListPalette(encoded.bits(), encoded.palette());
                target.storage = new BitStorage(encoded.bits(), PalettedSection.SIZE, encoded.data());
            }
            case MAP -> {
                MapPalette palette = new MapPalette(encoded.bits());
                for (int state : encoded.palette()) {
                    palette.stateToId(state); // ids are handed out in order, so they match the packed indices
                }
                target.palette = palette;
                target.storage = new BitStorage(encoded.bits(), PalettedSection.SIZE, encoded.data());
            }
            case GLOBAL -> {
                target.palette = GlobalPalette.INSTANCE;
                target.storage = new BitStorage(encoded.bits(), PalettedSection.SIZE, encoded.data());
            }
        }
    }
}
