package com.aitchn.prism.validation.mekanism.logic;

/**
 * What one face of a machine does for chemical pipes. {@code ALL} is the default: any tank may be filled or drained
 * through the face. The other modes pick one tank, so a machine with two gas inputs can have its left face on the first
 * tank and its right face on the second, and several pipe networks can touch the same machine without mixing gases.
 */
public enum Side {
    ALL("All tanks"),
    IN1("First input tank"),
    IN2("Second input tank"),
    OUT1("First output tank"),
    OUT2("Second output tank"),
    OFF("Nothing");

    private final String label;

    Side(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public Side next() {
        Side[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** Face numbers follow {@link Pos#neighbors()}: east, west, up, down, south, north. */
    public static final String[] FACE_NAMES = {"east", "west", "up", "down", "south", "north"};
}
