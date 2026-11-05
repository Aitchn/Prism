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

    private final Map<Pos, Machine> machines = new HashMap<>();
    private final Map<Pos, SolarGenerator> solars = new HashMap<>();
    private final Set<Pos> cables = new HashSet<>();
    private final Set<Pos> pipes = new HashSet<>();
    private List<Networks.Network> cableNets = List.of();
    private List<Networks.Network> pipeNets = List.of();
    private boolean topologyChanged = true;
    private long energyMoved;
    private long chemicalMoved;

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
            topologyChanged = false;
        }
    }

    /** One tick of cable and pipe transfer. Machines tick separately (each on its own block's schedule). */
    public void tickNetworks() {
        rebuildIfNeeded();
        energyMoved = Networks.distributeEnergy(cableNets,
                p -> solars.containsKey(p) ? solars.get(p).energy() : null,
                p -> machines.containsKey(p) ? machines.get(p).energy() : null, CABLE_LIMIT);
        chemicalMoved = Networks.distributeChemical(pipeNets,
                p -> machines.containsKey(p) ? machines.get(p).ports().outputs() : null,
                p -> machines.containsKey(p) ? machines.get(p).ports().inputs() : null, PIPE_LIMIT);
    }
}
