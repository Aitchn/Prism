package com.aitchn.prism.client;

import com.github.retrooper.packetevents.PacketEvents;

/**
 * Entry point of the outgoing block packet rewriting. Kept apart from the listener so that callers compile without
 * PacketEvents on their classpath: nothing here exposes a PacketEvents type.
 */
public final class PacketRewriting {

    private PacketRewriting() {}

    /**
     * Registers the rewriter that shows every custom block its look (see {@link BlockPacketRewriter}).
     *
     * @return a handle that unregisters it
     */
    public static Runnable install(LookSource source, ForeignChangeListener foreign) {
        BlockPacketRewriter rewriter = BlockPacketRewriter.create(source, foreign);
        PacketEvents.getAPI().getEventManager().registerListener(rewriter);
        return () -> PacketEvents.getAPI().getEventManager().unregisterListener(rewriter);
    }
}
