package com.aitchn.prism.api.menu;

/** What a slot of a {@link ContainerMenu} is for. Prism enforces it for every kind of click, drag and shift-click. */
public enum SlotRole {
    /** Shows an item; clicks go to {@link ContainerMenu#onButton}, items never move. The default. */
    BUTTON,
    /** Players may put items in (when {@link ContainerMenu#accepts} says so) and take them out. */
    INPUT,
    /** Players may only take items out, like a furnace's result slot. */
    OUTPUT
}
