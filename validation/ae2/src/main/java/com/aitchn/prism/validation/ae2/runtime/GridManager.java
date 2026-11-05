package com.aitchn.prism.validation.ae2.runtime;

import com.aitchn.prism.validation.ae2.crafting.Pattern;
import com.aitchn.prism.validation.ae2.grid.GridCalculator;
import com.aitchn.prism.validation.ae2.grid.GridNetwork;
import com.aitchn.prism.validation.ae2.grid.GridPos;
import com.aitchn.prism.validation.ae2.grid.NodeKind;
import com.aitchn.prism.validation.ae2.storage.NetworkStorage;
import com.aitchn.prism.validation.ae2.storage.StorageCell;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps the set of {@link NetworkRuntime}s in step with the placed blocks. Changes arrive from any region thread
 * (block placed, removed, drive edited); every change recomputes all networks under one lock, which is cheap at the
 * sizes a plugin sees and avoids cross-region reads of the world.
 */
public final class GridManager {

    private final Ae2State state;
    private volatile Map<GridPos, NetworkRuntime> byMember = Map.of();
    private volatile List<NetworkRuntime> runtimes = List.of();

    public GridManager(@NotNull Ae2State state) {
        this.state = state;
    }

    public @NotNull List<NetworkRuntime> networks() {
        return runtimes;
    }

    public @NotNull Optional<NetworkRuntime> networkAt(@NotNull GridPos pos) {
        return Optional.ofNullable(byMember.get(pos));
    }

    public void add(@NotNull GridPos pos, @NotNull NodeKind kind) {
        state.addNode(pos, kind);
        refresh();
    }

    public void remove(@NotNull GridPos pos) {
        state.removeNode(pos);
        refresh();
    }

    /** Recomputes topology, storage and patterns. Call after any change to nodes, drives or providers. */
    public synchronized void refresh() {
        List<GridNetwork> networks = GridCalculator.compute(state.nodes());
        Map<GridPos, NetworkRuntime> oldById = new HashMap<>();
        Map<NetworkRuntime, Double> oldEnergy = new HashMap<>();
        Map<NetworkRuntime, java.util.Set<GridPos>> oldMembers = new HashMap<>();
        for (NetworkRuntime runtime : runtimes) {
            oldMembers.put(runtime, java.util.Set.copyOf(runtime.topology().kinds().keySet()));
            oldById.put(runtime.id(), runtime);
            oldEnergy.put(runtime, runtime.power().stored());
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
                energy.put(runtime, state.energy(id));
            } else {
                reused.add(runtime);
                energy.put(runtime, 0.0);
            }
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
                    .max(java.util.Comparator.<Map.Entry<NetworkRuntime, Integer>>comparingInt(Map.Entry::getValue)
                            .thenComparing(e -> e.getKey().id(), java.util.Comparator.reverseOrder()))
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
    }

    private NetworkStorage storageOf(GridNetwork network) {
        List<StorageCell> cells = new ArrayList<>();
        for (GridPos drive : network.online(NodeKind.DRIVE)) {
            cells.addAll(state.driveCells(drive));
        }
        return new NetworkStorage(cells);
    }

    private List<Pattern> patternsOf(GridNetwork network) {
        List<Pattern> patterns = new ArrayList<>();
        for (GridPos provider : network.online(NodeKind.PATTERN_PROVIDER)) {
            patterns.addAll(state.patterns(provider));
        }
        return patterns;
    }

    /** One server tick: power drain and crafting progress for every network; also mirrors energy into the state. */
    public void tick() {
        for (NetworkRuntime runtime : runtimes) {
            if (runtime.tick()) {
                runtime.cells().forEach(state::markCell);
            }
        }
    }

    /** Copies each network's energy into the persistent state; call before a flush. */
    public void saveEnergy() {
        for (NetworkRuntime runtime : runtimes) {
            state.setEnergy(runtime.id(), runtime.power().stored());
        }
    }
}
