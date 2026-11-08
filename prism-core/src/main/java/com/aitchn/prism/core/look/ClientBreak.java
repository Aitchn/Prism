package com.aitchn.prism.core.look;

/**
 * How a Java client predicts breaking a block state, taken from the vanilla block: {@code hardness}, the tool type
 * that is preferred ({@code none} if no tool is) and whether drops need that tool. Instant blocks ({@code hardness 0})
 * cannot be slowed by an attribute.
 */
public record ClientBreak(double hardness, String tool, boolean requiresTool) {

    /** The client breaks instantly, whatever the player holds or any speed attribute. */
    public boolean instant() {
        return hardness == 0;
    }
}
