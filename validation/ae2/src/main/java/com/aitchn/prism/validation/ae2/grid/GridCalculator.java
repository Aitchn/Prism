package com.aitchn.prism.validation.ae2.grid;

import com.aitchn.prism.validation.ae2.grid.GridNetwork.CraftingCluster;
import com.aitchn.prism.validation.ae2.grid.GridNetwork.DeviceState;
import com.aitchn.prism.validation.ae2.grid.GridNetwork.Status;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jetbrains.annotations.NotNull;

/**
 * Splits all ME blocks into networks and hands out channels. Pure logic: it knows positions and kinds, nothing about
 * Bukkit.
 *
 * <p>Connectivity: conduits and crafting blocks connect to any touching ME block; devices connect only to touching
 * conduits or crafting blocks, never to each other. Channels travel only through conduits, from a controller to each
 * device along the shortest path; a conduit carries at most its {@link NodeKind#channelCapacity()}.
 */
public final class GridCalculator {

    private GridCalculator() {}

    public static @NotNull List<GridNetwork> compute(@NotNull Map<GridPos, NodeKind> nodes) {
        List<GridNetwork> networks = new ArrayList<>();
        Set<GridPos> seen = new HashSet<>();
        for (GridPos start : new TreeSet<>(nodes.keySet())) {
            if (seen.contains(start)) {
                continue;
            }
            Map<GridPos, NodeKind> members = new TreeMap<>();
            Deque<GridPos> queue = new ArrayDeque<>(List.of(start));
            seen.add(start);
            while (!queue.isEmpty()) {
                GridPos pos = queue.poll();
                members.put(pos, nodes.get(pos));
                for (GridPos next : pos.neighbors()) {
                    NodeKind other = nodes.get(next);
                    if (other != null && connects(nodes.get(pos), other) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            networks.add(calculate(members));
        }
        return networks;
    }

    private static boolean connects(NodeKind a, NodeKind b) {
        return a.role() != NodeKind.Role.DEVICE || b.role() != NodeKind.Role.DEVICE;
    }

    private static GridNetwork calculate(Map<GridPos, NodeKind> members) {
        Set<GridPos> controllers = new TreeSet<>();
        for (var entry : members.entrySet()) {
            if (entry.getValue() == NodeKind.CONTROLLER) {
                controllers.add(entry.getKey());
            }
        }
        List<CraftingCluster> clusters = clusters(members);
        Status status;
        if (controllers.isEmpty()) {
            status = Status.NO_CONTROLLER;
        } else if (controllerGroups(controllers) > 1) {
            status = Status.CONTROLLER_CONFLICT;
        } else {
            status = Status.ONLINE;
        }

        Map<GridPos, DeviceState> states = new HashMap<>();
        Map<GridPos, Integer> used = new HashMap<>();
        if (status != Status.ONLINE) {
            DeviceState offline = status == Status.NO_CONTROLLER ? DeviceState.NO_CONTROLLER
                    : DeviceState.CONTROLLER_CONFLICT;
            members.forEach((pos, kind) -> {
                if (kind.role() == NodeKind.Role.DEVICE) {
                    states.put(pos, offline);
                }
            });
            return new GridNetwork(members, status, states, used, clusters);
        }

        // Breadth-first tree over conduits, rooted at the controller blocks.
        Map<GridPos, Integer> depth = new HashMap<>();
        Map<GridPos, GridPos> parent = new HashMap<>();
        Deque<GridPos> queue = new ArrayDeque<>();
        for (GridPos controller : controllers) {
            depth.put(controller, 0);
            queue.add(controller);
        }
        while (!queue.isEmpty()) {
            GridPos pos = queue.poll();
            for (GridPos next : sorted(pos.neighbors())) {
                NodeKind kind = members.get(next);
                if (kind != null && kind.role() == NodeKind.Role.CONDUIT && !depth.containsKey(next)) {
                    depth.put(next, depth.get(pos) + 1);
                    parent.put(next, pos);
                    queue.add(next);
                }
            }
        }

        for (var entry : members.entrySet()) {
            GridPos pos = entry.getKey();
            NodeKind kind = entry.getValue();
            if (kind.role() != NodeKind.Role.DEVICE) {
                continue;
            }
            if (kind.channelsRequired() == 0) {
                states.put(pos, DeviceState.ONLINE);
                continue;
            }
            states.put(pos, assign(pos, kind, members, depth, parent, used) ? DeviceState.ONLINE
                    : DeviceState.NO_CHANNEL);
        }
        return new GridNetwork(members, status, states, used, clusters);
    }

    private static boolean assign(GridPos device, NodeKind kind, Map<GridPos, NodeKind> members,
            Map<GridPos, Integer> depth, Map<GridPos, GridPos> parent, Map<GridPos, Integer> used) {
        GridPos entry = null;
        for (GridPos next : sorted(device.neighbors())) {
            NodeKind other = members.get(next);
            if (other != null && other.role() == NodeKind.Role.CONDUIT && depth.containsKey(next)
                    && (entry == null || depth.get(next) < depth.get(entry))) {
                entry = next;
            }
        }
        if (entry == null) {
            return false;
        }
        List<GridPos> path = new ArrayList<>();
        for (GridPos at = entry; at != null; at = parent.get(at)) {
            path.add(at);
            if (used.getOrDefault(at, 0) + kind.channelsRequired() > members.get(at).channelCapacity()) {
                return false;
            }
        }
        for (GridPos at : path) {
            used.merge(at, kind.channelsRequired(), Integer::sum);
        }
        return true;
    }

    /** Number of separate groups of touching controller blocks. */
    private static int controllerGroups(Set<GridPos> controllers) {
        Set<GridPos> left = new TreeSet<>(controllers);
        int groups = 0;
        while (!left.isEmpty()) {
            groups++;
            Deque<GridPos> queue = new ArrayDeque<>(List.of(left.iterator().next()));
            left.remove(queue.peek());
            while (!queue.isEmpty()) {
                for (GridPos next : queue.poll().neighbors()) {
                    if (left.remove(next)) {
                        queue.add(next);
                    }
                }
            }
        }
        return groups;
    }

    private static List<CraftingCluster> clusters(Map<GridPos, NodeKind> members) {
        Set<GridPos> left = new TreeSet<>();
        members.forEach((pos, kind) -> {
            if (kind.role() == NodeKind.Role.CRAFTING) {
                left.add(pos);
            }
        });
        List<CraftingCluster> clusters = new ArrayList<>();
        while (!left.isEmpty()) {
            GridPos first = left.iterator().next();
            left.remove(first);
            Set<GridPos> cluster = new TreeSet<>(List.of(first));
            Deque<GridPos> queue = new ArrayDeque<>(List.of(first));
            long bytes = 0;
            int coprocessors = 0;
            while (!queue.isEmpty()) {
                GridPos pos = queue.poll();
                NodeKind kind = members.get(pos);
                bytes += kind.craftingBytes();
                if (kind == NodeKind.CRAFTING_COPROCESSOR) {
                    coprocessors++;
                }
                for (GridPos next : pos.neighbors()) {
                    if (left.remove(next)) {
                        cluster.add(next);
                        queue.add(next);
                    }
                }
            }
            clusters.add(new CraftingCluster(cluster, bytes, coprocessors));
        }
        return clusters;
    }

    private static List<GridPos> sorted(List<GridPos> positions) {
        return positions.stream().sorted().toList();
    }
}
