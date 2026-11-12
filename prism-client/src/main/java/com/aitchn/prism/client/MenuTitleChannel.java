package com.aitchn.prism.client;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Changes the title of the container a player has open, without closing it (ADR-0028). Paper's only way to do that,
 * {@code InventoryView#setTitle(String)}, is deprecated and takes legacy text, so the screen is opened again with the
 * same container id, menu type and a new title, as vanilla does when it reopens a screen; the caller then resends the
 * contents ({@code Player#updateInventory}), which also restores the carried item.
 *
 * <p>The container id and menu type are taken from the last open-screen packet the server sent each player
 * ({@link OpenWindowTracker}), so nothing about the server's internals is read. No PacketEvents type appears in this
 * class's signatures, so callers compile without PacketEvents. Safe from any thread; the send itself should come from
 * the player's thread so that it is ordered with the contents that follow.
 */
public final class MenuTitleChannel {

    /** The container id and menu type of the screen a player has open. */
    record Open(int containerId, int type) {}

    private final Map<UUID, Open> open = new ConcurrentHashMap<>();
    private final Runnable unregister;

    private MenuTitleChannel() {
        this.unregister = OpenWindowTracker.register(open);
    }

    /** Registers the listener that remembers each player's open container; {@link #uninstall} removes it. */
    public static MenuTitleChannel install() {
        return new MenuTitleChannel();
    }

    public void uninstall() {
        unregister.run();
        open.clear();
    }

    /**
     * Shows {@code title} on the container {@code player} has open, if the last screen the server opened for them is a
     * chest of {@code rows} rows (menu type {@code generic_9x<rows>}). Returns false when nothing was sent.
     */
    public boolean retitle(Player player, int rows, Component title) {
        Open current = open.get(player.getUniqueId());
        if (current == null || current.type() != rows - 1) {
            return false;
        }
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerOpenWindow(current.containerId(), current.type(), title));
        return true;
    }

    /** Forgets a player who left. */
    public void forget(UUID player) {
        open.remove(player);
    }
}
