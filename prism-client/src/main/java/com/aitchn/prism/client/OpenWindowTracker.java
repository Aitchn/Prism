package com.aitchn.prism.client;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import java.util.Map;
import java.util.UUID;

/** Records the container id and menu type of every open-screen packet sent to a player (for {@link MenuTitleChannel}). */
final class OpenWindowTracker extends PacketListenerAbstract {

    private final Map<UUID, MenuTitleChannel.Open> open;

    private OpenWindowTracker(Map<UUID, MenuTitleChannel.Open> open) {
        super(PacketListenerPriority.MONITOR);
        this.open = open;
    }

    /** Registers a tracker writing into {@code open}; returns a handle that unregisters it. */
    static Runnable register(Map<UUID, MenuTitleChannel.Open> open) {
        OpenWindowTracker tracker = new OpenWindowTracker(open);
        PacketEvents.getAPI().getEventManager().registerListener(tracker);
        return () -> PacketEvents.getAPI().getEventManager().unregisterListener(tracker);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.OPEN_WINDOW || event.isCancelled()) {
            return;
        }
        WrapperPlayServerOpenWindow wrapper = new WrapperPlayServerOpenWindow(event);
        UUID id = event.getUser().getUUID();
        if (id != null) {
            open.put(id, new MenuTitleChannel.Open(wrapper.getContainerId(), wrapper.getType()));
        }
    }
}
