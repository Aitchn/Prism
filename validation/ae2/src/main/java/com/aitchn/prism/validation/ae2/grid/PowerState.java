package com.aitchn.prism.validation.ae2.grid;

import org.jetbrains.annotations.NotNull;

/** The energy stored in one network's cells and the per-tick power check. Thread-safe. */
public final class PowerState {

    private double stored;

    public synchronized double stored() {
        return stored;
    }

    public synchronized void set(double value) {
        stored = Math.max(0, value);
    }

    /** Adds energy up to the network's capacity; returns how much was accepted. */
    public synchronized double inject(@NotNull GridNetwork network, double amount) {
        double room = Math.max(0, network.energyCapacity() - stored);
        double accepted = Math.min(room, Math.max(0, amount));
        stored += accepted;
        return accepted;
    }

    /**
     * Runs one tick of idle drain. Returns true when the network is powered. Without a controller or with a
     * controller conflict the network is never powered.
     */
    public synchronized boolean tick(@NotNull GridNetwork network) {
        if (network.status() != GridNetwork.Status.ONLINE) {
            return false;
        }
        if (network.hasCreativeEnergy()) {
            return true;
        }
        double drain = network.idleDrain();
        if (stored >= drain) {
            stored -= drain;
            return true;
        }
        stored = 0;
        return false;
    }
}
