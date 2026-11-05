package com.aitchn.prism.validation.mekanism.logic;

import java.util.Objects;
import java.util.function.Predicate;

/** Holds one kind of chemical at a time, optionally only kinds the owner accepts. */
public final class ChemicalTank {

    private final long capacity;
    private final Predicate<Chemical> accepts;
    private Chemical type;
    private long amount;

    public ChemicalTank(long capacity) {
        this(capacity, c -> true);
    }

    public ChemicalTank(long capacity, Predicate<Chemical> accepts) {
        this.capacity = capacity;
        this.accepts = accepts;
    }

    public Chemical type() {
        return type;
    }

    public long amount() {
        return amount;
    }

    public long capacity() {
        return capacity;
    }

    public long fill(Chemical chemical, long toAdd, boolean simulate) {
        Objects.requireNonNull(chemical);
        if (toAdd <= 0 || (type != null && !type.equals(chemical)) || !accepts.test(chemical)) {
            return 0;
        }
        long accepted = Math.min(toAdd, capacity - amount);
        if (!simulate && accepted > 0) {
            type = chemical;
            amount += accepted;
        }
        return accepted;
    }

    /** Drains up to {@code max} of whatever is inside (or only {@code only}, when non-null). */
    public long drain(Chemical only, long max, boolean simulate) {
        if (type == null || (only != null && !only.equals(type))) {
            return 0;
        }
        long taken = Math.min(max, amount);
        if (!simulate) {
            amount -= taken;
            if (amount == 0) {
                type = null;
            }
        }
        return taken;
    }

    public void set(Chemical chemical, long value) {
        long clamped = Math.max(0, Math.min(value, capacity));
        type = clamped > 0 ? chemical : null;
        amount = clamped;
    }
}
