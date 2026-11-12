package com.aitchn.prism.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Where the online players are, for the world effects (ADR-0027): world and position, written by each player's own
 * events (on the player's thread on Folia) and read from any thread without touching the player. The effects clocks
 * use it to decide whom a task must be scheduled for at all, so nobody near nothing costs anything.
 */
final class EffectViewers implements Listener {

    /** One player: volatile fields written by the player's events. */
    static final class Viewer {
        final Player player;
        final boolean bedrock;
        volatile UUID world;
        volatile double x;
        volatile double y;
        volatile double z;

        Viewer(Player player, boolean bedrock) {
            this.player = player;
            this.bedrock = bedrock;
            update(player.getLocation());
        }

        void update(Location at) {
            this.world = at.getWorld().getUID();
            this.x = at.getX();
            this.y = at.getY() + 1.62; // eye height: what a player sees particles from
            this.z = at.getZ();
        }

        double distanceSq(double px, double py, double pz) {
            double dx = x - px;
            double dy = y - py;
            double dz = z - pz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private final Map<UUID, Viewer> viewers = new ConcurrentHashMap<>();
    private final Predicate<UUID> bedrock;

    EffectViewers(Predicate<UUID> bedrock, Iterable<? extends Player> online) {
        this.bedrock = bedrock;
        for (Player player : online) {
            add(player);
        }
    }

    private void add(Player player) {
        viewers.put(player.getUniqueId(), new Viewer(player, bedrock.test(player.getUniqueId())));
    }

    boolean isEmpty() {
        return viewers.isEmpty();
    }

    /** The players in {@code world} right now, as last reported. */
    List<Viewer> in(UUID world) {
        List<Viewer> found = new ArrayList<>();
        for (Viewer viewer : viewers.values()) {
            if (world.equals(viewer.world)) {
                found.add(viewer);
            }
        }
        return found;
    }

    /** The tracked player {@code id}, or null. */
    @org.jetbrains.annotations.Nullable Viewer of(UUID id) {
        return viewers.get(id);
    }

    /** Every tracked player. */
    List<Viewer> all() {
        return new ArrayList<>(viewers.values());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        add(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Viewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer != null) {
            viewer.update(event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Viewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer != null) {
            viewer.update(event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Viewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer != null) {
            viewer.update(event.getRespawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Viewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer != null) {
            viewer.update(event.getPlayer().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        viewers.remove(event.getPlayer().getUniqueId());
    }
}
