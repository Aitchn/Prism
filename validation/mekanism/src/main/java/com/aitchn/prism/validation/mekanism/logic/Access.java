package com.aitchn.prism.validation.mekanism.logic;

/** What one face of a machine does for item transporters: both ways, only in, only out, or nothing. */
public enum Access {
    ALL("Input and output"),
    INPUT("Input only"),
    OUTPUT("Output only"),
    OFF("Nothing");

    private final String label;

    Access(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canInsert() {
        return this == ALL || this == INPUT;
    }

    public boolean canExtract() {
        return this == ALL || this == OUTPUT;
    }

    public Access next() {
        Access[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
