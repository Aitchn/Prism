package com.aitchn.prism.client;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import org.bukkit.entity.Player;

/**
 * Sends a block's look to one player without reading or changing the world, so it is safe from any thread. Used to
 * correct what the client predicted on its own (it recomputes neighbour shapes locally and the server never resends).
 */
public final class LookSender {

    private LookSender() {}

    /** Shows {@code player} block state {@code state} (a look id of a {@link com.aitchn.prism.core.block.BlockLook}). */
    public static void send(Player player, int x, int y, int z, int state) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerBlockChange(new Vector3i(x, y, z), state));
    }
}
