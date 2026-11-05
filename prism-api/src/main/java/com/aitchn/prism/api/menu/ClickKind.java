package com.aitchn.prism.api.menu;

/** How a button was clicked. Bedrock players (through Geyser) mostly produce {@link #LEFT} and {@link #SHIFT_LEFT}. */
public enum ClickKind {
    LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, MIDDLE, NUMBER_KEY, DROP, DOUBLE, OTHER
}
