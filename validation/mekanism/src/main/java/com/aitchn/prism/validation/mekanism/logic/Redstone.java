package com.aitchn.prism.validation.mekanism.logic;

/** When a machine may work relative to a redstone signal on it. */
public enum Redstone {
    IGNORE("Ignore redstone"),
    HIGH("Run with a signal"),
    LOW("Run without a signal");

    private final String label;

    Redstone(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean allows(boolean powered) {
        return this == IGNORE || (this == HIGH) == powered;
    }

    public Redstone next() {
        Redstone[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
