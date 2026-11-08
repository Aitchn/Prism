package com.aitchn.prism.validation.ae2.runtime;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.validation.ae2.Ae2Content;
import com.aitchn.prism.validation.ae2.crafting.Pattern;
import com.aitchn.prism.validation.ae2.grid.GridCalculator;
import com.aitchn.prism.validation.ae2.grid.GridNetwork;
import com.aitchn.prism.validation.ae2.grid.GridPos;
import com.aitchn.prism.validation.ae2.grid.NodeKind;
import com.aitchn.prism.validation.ae2.storage.NetworkStorage;
import com.aitchn.prism.validation.ae2.storage.StorageCell;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the set of {@link NetworkRuntime}s in step with the ME blocks that are loaded. Prism tells the mod when a block
 * is placed, removed, loaded or unloaded; nodes of unloaded chunks leave the grid, exactly as in AE2. Every change
 * recomputes all networks under one lock, which is cheap at plugin scale and keeps the world untouched: world access
 * (block looks, saving) is queued to the owning region with {@code Prism.scheduler()}.
 */
public final class GridManager {

    private static final int FLUSH_EVERY_TICKS = 100;

    private final Map<GridPos, NodeKind> nodes = new ConcurrentHashMap<>();
    private final Map<GridPos, DriveState> drives = new ConcurrentHashMap<>();
    private final Map<GridPos, List<Pattern>> providers = new ConcurrentHashMap<>();
    private final Map<GridPos, Double> pendingEnergy = new ConcurrentHashMap<>();
    private final Map<GridPos, Boolean> shownOnline = new ConcurrentHashMap<>();
    private volatile Map<GridPos, NetworkRuntime> byMember = Map.of();
    private volatile List<NetworkRuntime> runtimes = List.of();

    public @NotNull List<NetworkRuntime> networks() {
        return runtimes;
    }

    public @NotNull Optional<NetworkRuntime> networkAt(@NotNull GridPos pos) {
        return Optional.ofNullable(byMember.get(pos));
    }

    public @NotNull Optional<DriveState> drive(@NotNull GridPos pos) {
        return Optional.ofNullable(drives.get(pos));
    }

    public boolean contains(@NotNull GridPos pos) {
        return nodes.containsKey(pos);
    }

    /**
     * Adds a block to the grid. Does nothing if it is already there, so a place event followed by a load event for the
     * same block is harmless.
     *
     * @param drive    the cells of a drive
     * @param patterns the patterns of a pattern provider
     * @param energy   the energy an energy cell had stored
     */
    public synchronized void attach(@NotNull GridPos pos, @NotNull NodeKind kind, @Nullable DriveState drive,
            @NotNull List<Pattern> patterns, double energy) {
        if (nodes.putIfAbsent(pos, kind) != null) {
            return;
        }
        if (drive != null) {
            drives.put(pos, drive);
        }
        if (kind == NodeKind.PATTERN_PROVIDER) {
            providers.put(pos, List.copyOf(patterns));
        }
        if (energy > 0) {
            pendingEnergy.put(pos, energy);
        }
        refresh();
    }

    /** Removes a block from the grid. */
    public synchronized void detach(@NotNull GridPos pos) {
        if (nodes.remove(pos) == null) {
            return;
        }
        drives.remove(pos);
        providers.remove(pos);
        pendingEnergy.remove(pos);
        shownOnline.remove(pos);
        refresh();
    }

    public synchronized void setPatterns(@NotNull GridPos pos, @NotNull List<Pattern> patterns) {
        if (providers.containsKey(pos)) {
            providers.put(pos, List.copyOf(patterns));
            refresh();
        }
    }

    /**
     * The part of the network's stored energy that belongs to the energy cell at {@code pos} (by capacity). With
     * {@code take} the share is also removed from the network, for a cell that is leaving.
     */
    public double energyShare(@NotNull GridPos pos, boolean take) {
        NetworkRuntime runtime = byMember.get(pos);
        NodeKind kind = nodes.get(pos);
        if (runtime == null || kind != NodeKind.ENERGY_CELL) {
            return 0;
        }
        long total = runtime.topology().energyCapacity();
        if (total <= 0 || total == Long.MAX_VALUE) {
            return 0;
        }
        double share = runtime.power().stored() * kind.energyCapacity() / total;
        if (take) {
            runtime.power().set(runtime.power().stored() - share);
        }
        return share;
    }

    /** Marks every drive of {@code runtime} so the next flush stores its cells. */
    public void markStorageDirty(@NotNull NetworkRuntime runtime) {
        for (GridPos pos : runtime.topology().online(NodeKind.DRIVE)) {
            DriveState drive = drives.get(pos);
            if (drive != null) {
                drive.markDirty();
            }
        }
    }

    /** Recomputes topology, storage and patterns. Call after any change to nodes, drives or providers. */
    public synchronized void refresh() {
        List<GridNetwork> networks = GridCalculator.compute(Map.copyOf(nodes));
        Map<GridPos, NetworkRuntime> oldById = new HashMap<>();
        Map<NetworkRuntime, Double> oldEnergy = new HashMap<>();
        Map<NetworkRuntime, Set<GridPos>> oldMembers = new HashMap<>();
        for (NetworkRuntime runtime : runtimes) {
            oldById.put(runtime.id(), runtime);
            oldEnergy.put(runtime, runtime.power().stored());
            oldMembers.put(runtime, Set.copyOf(runtime.topology().kinds().keySet()));
        }

        List<NetworkRuntime> next = new ArrayList<>();
        Map<GridPos, NetworkRuntime> members = new HashMap<>();
        Map<NetworkRuntime, Double> energy = new HashMap<>();
        List<NetworkRuntime> reused = new ArrayList<>();
        Map<NetworkRuntime, GridNetwork> newTopologies = new HashMap<>();
        for (GridNetwork network : networks) {
            GridPos id = new TreeSet<>(network.kinds().keySet()).first();
            NetworkRuntime runtime = oldById.get(id);
            if (runtime == null) {
                runtime = new NetworkRuntime(id, network);
            } else {
                reused.add(runtime);
            }
            energy.put(runtime, 0.0);
            runtime.update(network, storageOf(network), patternsOf(network));
            newTopologies.put(runtime, network);
            next.add(runtime);
            for (GridPos pos : network.kinds().keySet()) {
                members.put(pos, runtime);
            }
        }

        // Each old network hands its energy to the new network that contains most of its blocks (merge or split).
        for (NetworkRuntime old : runtimes) {
            Map<NetworkRuntime, Integer> overlap = new HashMap<>();
            for (GridPos pos : oldMembers.get(old)) {
                NetworkRuntime now = members.get(pos);
                if (now != null) {
                    overlap.merge(now, 1, Integer::sum);
                }
            }
            overlap.entrySet().stream()
                    .max(Comparator.<Map.Entry<NetworkRuntime, Integer>>comparingInt(Map.Entry::getValue)
                            .thenComparing(e -> e.getKey().id(), Comparator.reverseOrder()))
                    .ifPresent(best -> energy.merge(best.getKey(), oldEnergy.get(old), Double::sum));
        }
        energy.forEach((runtime, stored) ->
                runtime.power().set(newTopologies.get(runtime).status() == GridNetwork.Status.ONLINE
                        ? Math.min(stored, newTopologies.get(runtime).energyCapacity()) : stored));

        for (NetworkRuntime old : runtimes) {
            if (!reused.contains(old)) {
                old.cancelAllJobs();
            }
        }
        next.forEach(NetworkRuntime::dropOrphanedJobs);
        runtimes = List.copyOf(next);
        byMember = Map.copyOf(members);

        // Energy that cells carried in their block data when they loaded.
        pendingEnergy.forEach((pos, stored) -> {
            NetworkRuntime runtime = members.get(pos);
            if (runtime != null) {
                runtime.power().inject(runtime.topology(), stored);
            }
        });
        pendingEnergy.clear();

        updateLooks(members);
    }

    /** Switches the block variant of devices whose online state changed. Runs the world part on the block's thread. */
    private void updateLooks(Map<GridPos, NetworkRuntime> members) {
        for (var entry : Map.copyOf(nodes).entrySet()) {
            GridPos pos = entry.getKey();
            NodeKind kind = entry.getValue();
            if (!hasLooks(kind)) {
                continue;
            }
            NetworkRuntime runtime = members.get(pos);
            boolean online = runtime != null && (kind == NodeKind.CONTROLLER
                    ? runtime.topology().status() == GridNetwork.Status.ONLINE : runtime.topology().isOnline(pos));
            Boolean previous = shownOnline.put(pos, online);
            Location at = location(pos);
            if (at != null && (previous == null || previous != online)) {
                String variant = online ? "online" : "offline";
                Prism.scheduler().runAt(at, () -> Prism.blocks().setVariant(at.getBlock(), variant));
            }
        }
    }

    private static boolean hasLooks(NodeKind kind) {
        return kind == NodeKind.CONTROLLER || kind == NodeKind.DRIVE || kind == NodeKind.TERMINAL
                || kind == NodeKind.PATTERN_PROVIDER;
    }

    private NetworkStorage storageOf(GridNetwork network) {
        List<StorageCell> cells = new ArrayList<>();
        for (GridPos drive : network.online(NodeKind.DRIVE)) {
            DriveState state = drives.get(drive);
            if (state != null) {
                cells.addAll(state.cells());
            }
        }
        return new NetworkStorage(cells);
    }

    private List<Pattern> patternsOf(GridNetwork network) {
        List<Pattern> patterns = new ArrayList<>();
        for (GridPos provider : network.online(NodeKind.PATTERN_PROVIDER)) {
            patterns.addAll(providers.getOrDefault(provider, List.of()));
        }
        return patterns;
    }

    /**
     * Called by a controller's tick behavior on its own region thread. Only the network's first controller drives it,
     * so a network ticks once however many controller blocks it has.
     */
    public void tickFrom(@NotNull GridPos controller) {
        NetworkRuntime runtime = byMember.get(controller);
        if (runtime == null) {
            return;
        }
        GridPos first = runtime.topology().kinds().entrySet().stream()
                .filter(e -> e.getValue() == NodeKind.CONTROLLER).map(Map.Entry::getKey).sorted().findFirst()
                .orElse(controller);
        if (!first.equals(controller)) {
            return;
        }
        if (runtime.tick()) {
            markStorageDirty(runtime);
        }
        if (runtime.ticks() % FLUSH_EVERY_TICKS == 0) {
            flush(runtime);
        }
    }

    /** Queues the writing of changed drives and of every energy cell's share to the blocks' own threads. */
    private void flush(NetworkRuntime runtime) {
        for (GridPos pos : runtime.topology().online(NodeKind.DRIVE)) {
            DriveState drive = drives.get(pos);
            if (drive != null && drive.takeDirty()) {
                persist(pos, data -> drive.write(data));
            }
        }
        for (var entry : runtime.topology().kinds().entrySet()) {
            if (entry.getValue() == NodeKind.ENERGY_CELL) {
                double share = energyShare(entry.getKey(), false);
                persist(entry.getKey(), data -> data.set(Ae2Content.ENERGY, PersistentDataType.DOUBLE, share));
            }
        }
    }

    /** Runs {@code write} on the thread that owns {@code pos}, against that block's per-block data. */
    public void persist(@NotNull GridPos pos, @NotNull java.util.function.Consumer<org.bukkit.persistence.PersistentDataContainer> write) {
        Location at = location(pos);
        if (at == null) {
            return;
        }
        Prism.scheduler().runAt(at, () -> Prism.blocks().get(at.getBlock()).ifPresent(block -> write.accept(block.data())));
    }

    public static @Nullable Location location(@NotNull GridPos pos) {
        org.bukkit.World world = Bukkit.getWorld(pos.world());
        return world == null ? null : new Location(world, pos.x(), pos.y(), pos.z());
    }
}
