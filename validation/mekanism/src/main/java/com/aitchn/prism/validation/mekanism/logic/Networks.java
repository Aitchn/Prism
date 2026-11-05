package com.aitchn.prism.validation.mekanism.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Cable and pipe networks computed from the set of conduit positions. A network is a face-connected group of
 * conduits plus the blocks touching it. The grid rebuilds them only when a conduit or endpoint appears or goes, not
 * every tick.
 */
public final class Networks {

    private Networks() {}

    /** One connected group: its conduits and the non-conduit positions touching them. */
    public record Network(Set<Pos> conduits, Set<Pos> endpoints, Set<Endpoint> touches) {
        public Network(Set<Pos> conduits, Set<Pos> endpoints) {
            this(conduits, endpoints, Set.of());
        }
    }

    /** A block position and which of its faces ({@link Pos#neighbors()} order) a conduit touches. */
    public record Endpoint(Pos pos, int face) {}

    /** The connected components of {@code conduits} (face-adjacent) with their endpoints. */
    public static List<Network> build(Set<Pos> conduits) {
        return components(conduits).stream()
                .map(c -> new Network(c, endpoints(c, conduits), touches(c, conduits))).toList();
    }

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

    /** The faces of non-conduit blocks that touch the network, in stable order. */
    public static Set<Endpoint> touches(Set<Pos> component, Set<Pos> allConduits) {
        Set<Endpoint> out = new LinkedHashSet<>();
        for (Pos conduit : component) {
            List<Pos> around = conduit.neighbors();
            for (int i = 0; i < around.size(); i++) {
                if (!allConduits.contains(around.get(i))) {
                    out.add(new Endpoint(around.get(i), i ^ 1)); // the neighbour's face that points back at the conduit
                }
            }
        }
        return out;
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

    /** The network that contains {@code conduit}, or null. */
    public static Network containing(List<Network> networks, Pos conduit) {
        for (Network network : networks) {
            if (network.conduits().contains(conduit)) {
                return network;
            }
        }
        return null;
    }

    public static long distributeEnergy(Set<Pos> cables, Function<Pos, EnergyBuffer> sources,
            Function<Pos, EnergyBuffer> sinks, long perCableLimit) {
        return distributeEnergy(build(cables), sources, sinks, perCableLimit);
    }

    /**
     * Moves energy within each network: everything {@code sources} can give is split evenly across the
     * {@code sinks} that accept it. Returns the total moved.
     */
    public static long distributeEnergy(List<Network> networks, Function<Pos, EnergyBuffer> sources,
            Function<Pos, EnergyBuffer> sinks, long perCableLimit) {
        long moved = 0;
        for (Network network : networks) {
            long limit = perCableLimit * network.conduits().size();
            List<EnergyBuffer> givers = new ArrayList<>();
            List<EnergyBuffer> takers = new ArrayList<>();
            for (Pos end : network.endpoints()) {
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

    public static long distributeChemical(Set<Pos> pipes, Function<Pos, List<ChemicalTank>> from,
            Function<Pos, List<ChemicalTank>> to, long perPipeLimit) {
        return distributeChemical(build(pipes), from, to, perPipeLimit);
    }

    /** Pipes: moves chemicals from output tanks to input tanks of other blocks inside each network. */
    public static long distributeChemical(List<Network> networks, Function<Pos, List<ChemicalTank>> from,
            Function<Pos, List<ChemicalTank>> to, long perPipeLimit) {
        long moved = 0;
        for (Network network : networks) {
            long budget = perPipeLimit * network.conduits().size();
            List<Pos> ends = new ArrayList<>(network.endpoints());
            for (Pos source : ends) {
                List<ChemicalTank> sources = from.apply(source);
                if (sources == null) {
                    continue;
                }
                for (ChemicalTank src : sources) {
                    for (Pos target : ends) {
                        List<ChemicalTank> targets = to.apply(target);
                        if (targets == null || target.equals(source)) {
                            continue;
                        }
                        for (ChemicalTank dst : targets) {
                            if (src.type() == null || budget <= 0) {
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
            }
        }
        return moved;
    }

    /**
     * Pipes with faces: like {@link #distributeChemical(List, Function, Function, long)} but the tanks a block offers
     * depend on which face the pipe touches, and a block never feeds itself through two of its own faces. A tank that
     * can give to several places (through several faces or several networks) shares what it has between the places
     * that currently accept it, so one thirsty machine cannot starve the others. {@code round} (any number that
     * changes every tick) rotates who gets the odd unit when the amount does not divide evenly.
     */
    public static long distributeChemicalByFace(List<Network> networks, Function<Endpoint, List<ChemicalTank>> from,
            Function<Endpoint, List<ChemicalTank>> to, long perPipeLimit, long round) {
        record Move(ChemicalTank src, ChemicalTank dst, int network) {}
        List<Move> moves = new ArrayList<>();
        long[] budget = new long[networks.size()];
        for (int n = 0; n < networks.size(); n++) {
            Network network = networks.get(n);
            budget[n] = perPipeLimit * network.conduits().size();
            List<Endpoint> ends = new ArrayList<>(network.touches());
            for (Endpoint source : ends) {
                List<ChemicalTank> sources = from.apply(source);
                if (sources == null) {
                    continue;
                }
                for (ChemicalTank src : sources) {
                    for (Endpoint target : ends) {
                        List<ChemicalTank> targets = target.pos().equals(source.pos()) ? null : to.apply(target);
                        if (targets == null) {
                            continue;
                        }
                        for (ChemicalTank dst : targets) {
                            if (dst != src) {
                                moves.add(new Move(src, dst, n));
                            }
                        }
                    }
                }
            }
        }
        java.util.IdentityHashMap<ChemicalTank, Integer> acceptors = new java.util.IdentityHashMap<>();
        java.util.IdentityHashMap<ChemicalTank, Long> offered = new java.util.IdentityHashMap<>();
        for (Move move : moves) {
            if (move.src().type() != null && move.dst().fill(move.src().type(), 1, true) > 0) {
                acceptors.merge(move.src(), 1, Integer::sum);
            }
            offered.putIfAbsent(move.src(), move.src().drain(null, Long.MAX_VALUE, true));
        }
        long moved = 0;
        java.util.IdentityHashMap<ChemicalTank, Integer> seen = new java.util.IdentityHashMap<>();
        for (Move move : moves) {
            Chemical type = move.src().type();
            int count = acceptors.getOrDefault(move.src(), 0);
            if (type == null || count == 0 || budget[move.network()] <= 0
                    || move.dst().fill(type, 1, true) == 0) {
                continue;
            }
            int index = seen.merge(move.src(), 1, Integer::sum) - 1;
            long total = offered.get(move.src());
            long share = total / count + (Math.floorMod(index + round, count) < total % count ? 1 : 0);
            if (share == 0) {
                continue;
            }
            long possible = Math.min(Math.min(share, budget[move.network()]), move.src().drain(null, share, true));
            long accepted = move.dst().fill(type, possible, false);
            move.src().drain(null, accepted, false);
            budget[move.network()] -= accepted;
            moved += accepted;
        }
        // Shares a full destination could not take are offered to the others, so a nearly full
        // consumer never starves the rest of the network.
        for (Move move : moves) {
            Chemical type = move.src().type();
            if (type == null || budget[move.network()] <= 0) {
                continue;
            }
            long possible = Math.min(budget[move.network()], move.src().drain(null, Long.MAX_VALUE, true));
            long accepted = possible <= 0 ? 0 : move.dst().fill(type, possible, false);
            if (accepted > 0) {
                move.src().drain(null, accepted, false);
                budget[move.network()] -= accepted;
                moved += accepted;
            }
        }
        return moved;
    }

    /** Convenience for tests: a map-backed lookup. */
    public static <V> Function<Pos, V> lookup(Map<Pos, V> map) {
        return map::get;
    }
}
