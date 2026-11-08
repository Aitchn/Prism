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
}
