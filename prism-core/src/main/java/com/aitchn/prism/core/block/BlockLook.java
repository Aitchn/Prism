package com.aitchn.prism.core.block;

/**
 * What the client is shown for one custom block position: the global block state id of the inert carrier the server
 * stores there, the id of the state (a vanilla state the resource pack redraws) a Java viewer must see instead, and, for
 * a look drawn by the display layer, the id of its {@code DisplayLook} ({@code -1} for a plain borrowed state). For a
 * display look {@code look} is the invisible shell state.
 *
 * <p>{@code bedrock} is the synthetic state id a Geyser viewer is shown instead ({@code -1} if the look has none, or
 * none is registered): a number above every vanilla state that only Geyser knows, mapped to a real Bedrock custom
 * block (ADR-0024). Java viewers never receive it.
 */
public record BlockLook(int carrier, int look, int display, int bedrock) {

    public static final int NO_DISPLAY = -1;
    public static final int NO_BEDROCK = -1;

    public BlockLook(int carrier, int look, int display) {
        this(carrier, look, display, NO_BEDROCK);
    }

    public BlockLook(int carrier, int look) {
        this(carrier, look, NO_DISPLAY, NO_BEDROCK);
    }

    public boolean displayed() {
        return display != NO_DISPLAY;
    }

    public boolean hasBedrock() {
        return bedrock != NO_BEDROCK;
    }

    /** The state a viewer is shown: the synthetic state for a Geyser viewer that has one, else the Java look. */
    public int shownTo(boolean bedrockViewer) {
        return bedrockViewer && hasBedrock() ? bedrock : look;
    }
}
