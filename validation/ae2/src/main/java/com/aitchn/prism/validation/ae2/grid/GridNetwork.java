package com.aitchn.prism.validation.ae2.grid;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;

/**
 * An immutable result of one grid calculation: one connected group of ME blocks.
 *
 * @param kinds   every member and what it is
 * @param status  whether the group has exactly one (possibly multi-block) controller
 * @param states  per device: online or why not
 * @param channelsUsed channels carried by each conduit
 * @param clusters crafting clusters that touch this network
 */
public record GridNetwork(
        @NotNull Map<GridPos, NodeKind> kinds,
        @NotNull Status status,
        @NotNull Map<GridPos, DeviceState> states,
        @NotNull Map<GridPos, Integer> channelsUsed,
        @NotNull List<CraftingCluster> clusters) {

    public enum Status { ONLINE, NO_CONTROLLER, CONTROLLER_CONFLICT }

    public enum DeviceState { ONLINE, NO_CHANNEL, NO_CONTROLLER, CONTROLLER_CONFLICT }

    /** A group of touching crafting blocks. A cluster without storage cannot run jobs. */
    public record CraftingCluster(@NotNull Set<GridPos> members, long bytes, int coprocessors) {
        public boolean valid() {
            return bytes > 0;
        }

        /** Crafting steps that can run at the same time. */
        public int parallelism() {
            return 1 + coprocessors;
        }
    }

    public GridNetwork {
        kinds = Map.copyOf(kinds);
        states = Map.copyOf(states);
        channelsUsed = Map.copyOf(channelsUsed);
        clusters = List.copyOf(clusters);
    }

    public boolean isOnline(@NotNull GridPos pos) {
        return states.get(pos) == DeviceState.ONLINE;
    }

    /** Online devices of {@code kind}, in position order. */
    public @NotNull List<GridPos> online(@NotNull NodeKind kind) {
        return new TreeMap<>(states).entrySet().stream()
                .filter(e -> e.getValue() == DeviceState.ONLINE && kinds.get(e.getKey()) == kind)
                .map(Map.Entry::getKey).toList();
    }

    /** AE per tick the online devices and controllers draw. */
    public double idleDrain() {
        double drain = 0;
        for (var entry : kinds.entrySet()) {
            NodeKind kind = entry.getValue();
            if (kind.role() == NodeKind.Role.DEVICE ? isOnline(entry.getKey()) : kind == NodeKind.CONTROLLER) {
                drain += kind.idlePower();
            }
        }
        return status == Status.ONLINE ? drain : 0;
    }

    /** Total energy the network's cells can hold; {@link Long#MAX_VALUE} with a creative cell. */
    public long energyCapacity() {
        long total = 0;
        for (var entry : kinds.entrySet()) {
            if (entry.getValue().energyCapacity() > 0 && status == Status.ONLINE) {
                long cap = entry.getValue().energyCapacity();
                total = cap == Long.MAX_VALUE || total > Long.MAX_VALUE - cap ? Long.MAX_VALUE : total + cap;
            }
        }
        return total;
    }

    public boolean hasCreativeEnergy() {
        return status == Status.ONLINE && kinds.containsValue(NodeKind.CREATIVE_ENERGY_CELL);
    }
}
