package com.aitchn.prism.validation.mekanism.logic;

/** Energy in joules (long). Rates cap what one call can move, like Mekanism's transfer limits. */
public final class EnergyBuffer {

    private final long capacity;
    private final long maxReceive;
    private final long maxExtract;
    private long stored;

    public EnergyBuffer(long capacity, long maxReceive, long maxExtract) {
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
    }

    public long stored() {
        return stored;
    }

    public long capacity() {
        return capacity;
    }

    public long receive(long amount, boolean simulate) {
        long accepted = Math.max(0, Math.min(Math.min(amount, maxReceive), capacity - stored));
        if (!simulate) {
            stored += accepted;
        }
        return accepted;
    }

    public long extract(long amount, boolean simulate) {
        long taken = Math.max(0, Math.min(Math.min(amount, maxExtract), stored));
        if (!simulate) {
            stored -= taken;
        }
        return taken;
    }

    /** Internal consumption by the owning machine; ignores the extract rate. Returns false if not enough stored. */
    public boolean consume(long amount) {
        if (stored < amount) {
            return false;
        }
        stored -= amount;
        return true;
    }

    /** Internal production (generators); ignores the receive rate. Returns what fit. */
    public long produce(long amount) {
        long accepted = Math.max(0, Math.min(amount, capacity - stored));
        stored += accepted;
        return accepted;
    }

    public void set(long value) {
        stored = Math.max(0, Math.min(value, capacity));
    }
}
