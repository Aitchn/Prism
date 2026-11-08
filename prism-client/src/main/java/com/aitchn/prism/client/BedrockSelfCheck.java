package com.aitchn.prism.client;

import com.aitchn.prism.core.block.BlockLook;
import com.aitchn.prism.core.block.BlockPositions;
import com.aitchn.prism.core.block.ChunkLooks;
import com.aitchn.prism.core.block.PalettedSection;
import com.aitchn.prism.core.block.SectionStates;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * A server-side check of the Geyser viewer rewrite against the real packet library, for the smoke test: a chunk
 * section is built with PacketEvents, rewritten the way the listener rewrites it for a Java viewer and for a Geyser
 * viewer, written to bytes and read back, and what comes back is compared position by position. It proves that the
 * synthetic states survive the packet library's own encoder and decoder at the width Geyser expects, and that the Java
 * rewrite never carries one.
 */
public final class BedrockSelfCheck {

    private BedrockSelfCheck() {}

    /**
     * @param blocks       chunk-local packed positions of one section to their look ({@code BlockLook.carrier} is what
     *                     the server holds there)
     * @param sectionY     the section's y index
     * @param fillerStates how many distinct vanilla states to scatter around them (300 forces the global palette)
     * @param geyserBits   the global palette width Geyser reads
     * @return null if everything held, else what did not
     */
    public static @Nullable String check(Map<Long, BlockLook> blocks, int sectionY, int fillerStates, int geyserBits) {
        ClientVersion version = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
        ChunkLooks looks = ChunkLooks.of(blocks);
        int vanilla = ClientStates.idCount();

        Chunk_v1_18 java = section(version, blocks, sectionY, fillerStates);
        Chunk_v1_18 bedrock = section(version, blocks, sectionY, fillerStates);
        int javaChanged = looks.rewriteSection(sectionY, new SectionStates() {
            @Override
            public int get(int x, int y, int z) {
                return java.getBlockId(x, y, z);
            }

            @Override
            public void set(int x, int y, int z, int state) {
                java.set(version, x, y, z, state);
            }
        }, ClientStates::isLegacyCarrier, (x, y, z) -> {});
        int bedrockChanged = BedrockSections.rewrite(bedrock, looks, sectionY, geyserBits,
                ClientStates::isLegacyCarrier, (x, y, z) -> {});
        if (javaChanged != blocks.size() || bedrockChanged < blocks.size()) {
            return "rewrote " + javaChanged + " (java) and " + bedrockChanged + " (bedrock) of " + blocks.size();
        }
        Chunk_v1_18 javaBack = roundTrip(java);
        Chunk_v1_18 bedrockBack = roundTrip(bedrock);
        for (Map.Entry<Long, BlockLook> entry : blocks.entrySet()) {
            int x = (int) ((entry.getKey() >> 4) & 15);
            int z = (int) (entry.getKey() & 15);
            int y = (int) (entry.getKey() >> 8) & 15;
            BlockLook look = entry.getValue();
            int seenByJava = javaBack.getBlockId(x, y, z);
            if (seenByJava != look.look() || seenByJava >= vanilla) {
                return "java viewer sees " + seenByJava + " at " + x + "," + y + "," + z + ", expected " + look.look();
            }
            int seenByBedrock = bedrockBack.getBlockId(x, y, z);
            int expected = look.shownTo(true);
            if (seenByBedrock != expected) {
                return "bedrock viewer sees " + seenByBedrock + " at " + x + "," + y + "," + z + ", expected " + expected;
            }
        }
        for (int i = 0; i < PalettedSection.SIZE; i++) {
            int x = i & 15;
            int z = (i >> 4) & 15;
            int y = i >> 8;
            if (!blocks.containsKey(BlockPositions.pack(x, sectionY * 16 + y, z))
                    && javaBack.getBlockId(x, y, z) != bedrockBack.getBlockId(x, y, z)) {
                return "the rewrite changed a block that is not a custom block at " + x + "," + y + "," + z;
            }
        }
        if (bedrockBack.getChunkData().palette instanceof GlobalPalette
                && bedrockBack.getChunkData().storage.getBitsPerEntry() != geyserBits) {
            return "global palette written with " + bedrockBack.getChunkData().storage.getBitsPerEntry()
                    + " bits, Geyser reads " + geyserBits;
        }
        return widthCheck(version, geyserBits);
    }

    /** A global-palette section written at the server's width is written again at Geyser's, with the same blocks. */
    private static @Nullable String widthCheck(ClientVersion version, int geyserBits) {
        Chunk_v1_18 wide = new Chunk_v1_18(version);
        int[] states = new int[PalettedSection.SIZE];
        for (int i = 0; i < states.length; i++) {
            states[i] = 1 + (i * 7) % 500;
        }
        BedrockSections.install(wide.getChunkData(), PalettedSection.encode(states, Math.max(9, geyserBits - 1)));
        wide.setBlockCount(PalettedSection.SIZE);
        boolean needed = wide.getChunkData().storage.getBitsPerEntry() != geyserBits;
        if (needed != BedrockSections.normalizeWidth(wide, geyserBits)) {
            return "width normalization did not match the width of the section";
        }
        Chunk_v1_18 back = roundTrip(wide);
        for (int i = 0; i < states.length; i++) {
            if (back.getBlockId(i & 15, i >> 8, (i >> 4) & 15) != states[i]) {
                return "width normalization changed block " + i;
            }
        }
        return back.getChunkData().storage.getBitsPerEntry() == geyserBits ? null
                : "global palette still " + back.getChunkData().storage.getBitsPerEntry() + " bits";
    }

    private static Chunk_v1_18 section(ClientVersion version, Map<Long, BlockLook> blocks, int sectionY,
            int fillerStates) {
        Chunk_v1_18 section = new Chunk_v1_18(version);
        blocks.forEach((packed, look) -> section.set(version, (int) ((packed >> 4) & 15), (int) (packed >> 8) & 15,
                (int) (packed & 15), look.carrier()));
        for (int i = 0; i < fillerStates; i++) {
            int x = (i * 5) & 15;
            int z = (i * 3 / 16) & 15;
            int y = (i * 11) % 16;
            if (!blocks.containsKey(BlockPositions.pack(x, sectionY * 16 + y, z)) && section.getBlockId(x, y, z) == 0) {
                section.set(version, x, y, z, 1 + i);
            }
        }
        return section;
    }

    /** Writes the section with the packet library and reads it back from the bytes. */
    private static Chunk_v1_18 roundTrip(Chunk_v1_18 section) {
        Object buffer = PacketEvents.getAPI().getNettyManager().getByteBufAllocationOperator().buffer();
        try {
            PacketWrapper<?> wrapper = PacketWrapper.createUniversalPacketWrapper(buffer);
            Chunk_v1_18.write(wrapper, section);
            return Chunk_v1_18.read(PacketWrapper.createUniversalPacketWrapper(buffer));
        } finally {
            ByteBufHelper.release(buffer);
        }
    }
}
