package com.aitchn.prism.validation.mekanism.logic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything that is wired together: machines, generators, cables and pipes by position, and the per-tick network
 * transfer. Bukkit-free so the server adapter ({@code Grid}) and the chain tests drive the very same code. Not
 * thread-safe: the owner serialises access.
 */
public final class Plant {

    public static final long CABLE_LIMIT = 4_096;
    public static final long PIPE_LIMIT = 256;
    /** Items per tick a transporter carries, per tier (basic, advanced, elite, ultimate). */
    public static final long[] TRANSPORTER_LIMITS = {4, 8, 16, 32};
    /** Tier multipliers shared by cables and pipes. */
    public static final long[] TIER_FACTORS = {1, 4, 16, 64};

    private final Map<Pos, Machine> machines = new HashMap<>();
    private final Map<Pos, SolarGenerator> solars = new HashMap<>();
    private final Set<Pos> cables = new HashSet<>();
    private final Set<Pos> pipes = new HashSet<>();
    private final Structures structures = new Structures();
    private final Set<Pos> transporters = new HashSet<>();
    private final Map<Pos, Integer> tiers = new HashMap<>();
    private final Map<Pos, Integer> colours = new HashMap<>();
    private List<Networks.Network> itemNets = List.of();
    private long itemsMoved;
    private List<Networks.Network> cableNets = List.of();
    private List<Networks.Network> pipeNets = List.of();
    private boolean topologyChanged = true;
    private long energyMoved;
    private long chemicalMoved;
    private long rounds;

    public Map<Pos, Machine> machines() {
        return machines;
    }

    public Map<Pos, SolarGenerator> solars() {
        return solars;
    }

    public Set<Pos> cables() {
        return cables;
    }

    public Set<Pos> pipes() {
        return pipes;
    }

    public Structures structures() {
        return structures;
    }

    public Set<Pos> transporters() {
        return transporters;
    }

    /** Tier (0 to 3) of a cable, pipe or transporter; 0 when never set. */
    public int tier(Pos pos) {
        return Math.max(0, Math.min(3, tiers.getOrDefault(pos, 0)));
    }

    public void setTier(Pos pos, int tier) {
        tiers.put(pos, tier);
        topologyChanged = true;
    }

    /** Colour of a transporter, or -1 for none. Transporters only join when their colours are equal. */
    public int colour(Pos pos) {
        return colours.getOrDefault(pos, -1);
    }

    public void setColour(Pos pos, int colour) {
        colours.put(pos, colour);
        topologyChanged = true;
    }

    /** Forget the per-conduit settings of a conduit that went away. */
    public void forgetConduit(Pos pos) {
        tiers.remove(pos);
        colours.remove(pos);
        topologyChanged = true;
    }

    public long itemsMoved() {
        return itemsMoved;
    }

    public List<Networks.Network> itemNets() {
        return itemNets;
    }

    /** Call whenever a machine, generator, cable or pipe appears or goes. */
    public void markDirty() {
        topologyChanged = true;
    }

    public long energyMoved() {
        return energyMoved;
    }

    public long chemicalMoved() {
        return chemicalMoved;
    }

    public List<Networks.Network> cableNets() {
        return cableNets;
    }

    public List<Networks.Network> pipeNets() {
        return pipeNets;
    }

    /** Recomputes the networks if a conduit or an endpoint appeared or went since the last call. */
    public void rebuildIfNeeded() {
        if (topologyChanged) {
            cableNets = Networks.build(cables);
            pipeNets = Networks.build(pipes);
            itemNets = Networks.build(transporters, (a, b) -> colour(a) == colour(b));
            structures.rebuild(p -> machines.containsKey(p) || solars.containsKey(p) || cables.contains(p)
                    || pipes.contains(p) || transporters.contains(p));
            topologyChanged = false;
        }
    }

    /** One tick of cable and pipe transfer. Machines tick separately (each on its own block's schedule). */
    public void tickNetworks() {
        rebuildIfNeeded();
        energyMoved = Networks.distributeEnergy(cableNets,
                p -> solars.containsKey(p) ? solars.get(p).energy()
                        : machines.containsKey(p) && machines.get(p).type().maxExtract() > 0
                                ? machines.get(p).energy() : null,
                p -> machines.containsKey(p) ? machines.get(p).energy() : null, n -> budget(n, CABLE_LIMIT));
        chemicalMoved = Networks.distributeChemicalByFace(pipeNets,
                e -> machines.containsKey(e.pos()) ? machines.get(e.pos()).giveTanks(e.face()) : valveTanks(e.pos()),
                e -> machines.containsKey(e.pos()) ? machines.get(e.pos()).takeTanks(e.face()) : valveTanks(e.pos()),
                n -> budget(n, PIPE_LIMIT), rounds);
        itemsMoved = ItemNetworks.distribute(itemNets,
                e -> machines.containsKey(e.pos()) ? machines.get(e.pos()).itemPort(e.face()) : null,
                n -> n.conduits().stream().mapToLong(p -> TRANSPORTER_LIMITS[tier(p)]).sum(), rounds);
        rounds++;
    }

    private long budget(Networks.Network network, long base) {
        return network.conduits().stream().mapToLong(p -> base * TIER_FACTORS[tier(p)]).sum();
    }

    private List<ChemicalTank> valveTanks(Pos pos) {
        ChemicalTank tank = structures.valveTank(pos);
        return tank == null ? null : List.of(tank);
    }
}
