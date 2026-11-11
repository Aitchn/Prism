package com.aitchn.prism.client;

import com.github.retrooper.packetevents.PacketEvents;

/**
 * Entry point of the packet rewriting (blocks, and item text per viewer). Kept apart from the listener so that callers compile without
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

    /**
     * Registers the rewriter that shows every Prism stack in its viewer's language and restores stacks clients send
     * (see {@link ItemTextRewriter}).
     *
     * @return a handle that unregisters it
     */
    public static Runnable installItemText(TextSource source) {
        ItemTextRewriter rewriter = new ItemTextRewriter(source);
        PacketEvents.getAPI().getEventManager().registerListener(rewriter);
        return () -> PacketEvents.getAPI().getEventManager().unregisterListener(rewriter);
    }
}
