package com.aitchn.prism.client;

import com.aitchn.prism.core.block.ChunkLooks;
import java.util.UUID;

/**
 * Where the packet rewriter finds the custom blocks. Called from network threads: implementations must answer from
 * immutable or concurrently readable data and never touch world state.
 */
public interface LookSource {

    /** The snapshot of one chunk; {@link ChunkLooks#EMPTY} if the chunk is unknown or has no custom blocks. */
    ChunkLooks chunk(UUID world, int chunkX, int chunkZ);

    /** Whether the world has any chunk with custom blocks. A cheap pre-check that lets chunk packets pass undecoded. */
    boolean anyIn(UUID world);

    /**
     * What real blocks of a borrowed family are shown as. Chunk and block packets are rewritten with it even in a world
     * without custom blocks, since a vanilla note block there would otherwise show a custom look.
     */
    default ClientCanon canon() {
        return ClientCanon.none();
    }

    /**
     * Whether synthetic Bedrock block states are registered with Geyser (ADR-0024). A cheap check made before asking
     * about a viewer; while it is false every viewer is rewritten as a Java client.
     */
    default boolean bedrockStates() {
        return false;
    }

    /** Whether {@code player} is a Geyser connection. Only asked while {@link #bedrockStates()} holds. */
    default boolean isBedrock(UUID player) {
        return false;
    }

    /**
     * Bits per block of the global palette Geyser reads, derived from the size of its block state registry once the
     * synthetic states are registered. A section a Geyser viewer receives in the global palette is written with this
     * width.
     */
    default int bedrockGlobalBits() {
        return 0;
    }
}
