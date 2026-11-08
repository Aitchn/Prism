package com.aitchn.prism.core.block;

/**
 * What the client is shown for one custom block position: the global block state id of the inert carrier the server
 * stores there, the id of the state (a vanilla state the resource pack redraws) a viewer must see instead, and, for a
 * look drawn by the display layer, the id of its {@code DisplayLook} ({@code -1} for a plain borrowed state). For a
 * display look {@code look} is the invisible shell state.
 */
public record BlockLook(int carrier, int look, int display) {

    public static final int NO_DISPLAY = -1;

    public BlockLook(int carrier, int look) {
        this(carrier, look, NO_DISPLAY);
    }

    public boolean displayed() {
        return display != NO_DISPLAY;
    }
}
