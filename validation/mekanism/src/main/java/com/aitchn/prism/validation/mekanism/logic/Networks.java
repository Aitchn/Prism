package com.aitchn.prism.validation.mekanism.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Cable and pipe networks computed from the set of cable positions. Recomputed by flood fill; fine for tests and
 * small worlds. A real storage-scale version needs incremental merge/split (see docs/gaps.md).
 */
public final class Networks {

    private Networks() {}

    /** The connected components of {@code cables} (face-adjacent). */
    public static List<Set<Pos>> components(Set<Pos> cables) {
        Set<Pos> seen = new LinkedHashSet<>();
        List<Set<Pos>> result = new ArrayList<>();
        for (Pos start : cables) {
            if (!seen.add(start)) {
                continue;
            }
            Set<Pos> component = new LinkedHashSet<>();
            ArrayDeque<Pos> queue = new ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                Pos current = queue.poll();
                component.add(current);
                for (Pos next : current.neighbors()) {
                    if (cables.contains(next) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            result.add(component);
        }
        return result;
    }

    /** Non-cable positions touching the network, in stable order. */
    public static Set<Pos> endpoints(Set<Pos> component, Set<Pos> allCables) {
        Set<Pos> out = new LinkedHashSet<>();
        for (Pos cable : component) {
            for (Pos n : cable.neighbors()) {
                if (!allCables.contains(n)) {
                    out.add(n);
                }
            }
        }
        return out;
    }

    /**
     * Moves energy within each network: everything {@code sources} can give is split evenly across the
     * {@code sinks} that accept it. Returns the total moved.
     */
    public static long distributeEnergy(Set<Pos> cables, Function<Pos, EnergyBuffer> sources,
            Function<Pos, EnergyBuffer> sinks, long perCableLimit) {
        long moved = 0;
        for (Set<Pos> component : components(cables)) {
            long limit = perCableLimit * component.size();
            List<EnergyBuffer> givers = new ArrayList<>();
            List<EnergyBuffer> takers = new ArrayList<>();
            for (Pos end : endpoints(component, cables)) {
                EnergyBuffer giver = sources.apply(end);
                if (giver != null && giver.extract(limit, true) > 0) {
                    givers.add(giver);
                }
                EnergyBuffer taker = sinks.apply(end);
                if (taker != null) {
                    takers.add(taker);
                }
            }
            for (EnergyBuffer giver : givers) {
                long available = Math.min(limit, giver.extract(limit, true));
                long left = available;
                int remaining = takers.size();
                for (EnergyBuffer taker : takers) {
                    if (taker == giver) {
                        remaining--;
                        continue;
                    }
                    long share = remaining > 0 ? Math.max(1, left / remaining) : 0;
                    long accepted = taker.receive(Math.min(share, left), false);
                    left -= accepted;
                    remaining--;
                }
                long used = available - left;
                giver.extract(used, false);
                moved += used;
            }
        }
        return moved;
    }

    /** Pipes: moves chemicals from {@code from} tanks to {@code to} tanks inside each network. */
    public static long distributeChemical(Set<Pos> pipes, Function<Pos, ChemicalTank> from,
            Function<Pos, ChemicalTank> to, long perPipeLimit) {
        long moved = 0;
        for (Set<Pos> component : components(pipes)) {
            long budget = perPipeLimit * component.size();
            List<Pos> ends = new ArrayList<>(endpoints(component, pipes));
            for (Pos source : ends) {
                ChemicalTank src = from.apply(source);
                if (src == null || src.type() == null) {
                    continue;
                }
                for (Pos target : ends) {
                    ChemicalTank dst = to.apply(target);
                    if (dst == null || dst == src || budget <= 0) {
                        continue;
                    }
                    long possible = Math.min(budget, src.drain(null, budget, true));
                    long accepted = dst.fill(src.type(), possible, false);
                    src.drain(null, accepted, false);
                    budget -= accepted;
                    moved += accepted;
                }
            }
        }
        return moved;
    }

    /** Convenience for tests: a map-backed lookup. */
    public static <V> Function<Pos, V> lookup(Map<Pos, V> map) {
        return map::get;
    }
}
