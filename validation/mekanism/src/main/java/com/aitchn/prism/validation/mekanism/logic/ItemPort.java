package com.aitchn.prism.validation.mekanism.logic;

/** What an item transporter sees of a block face: what it may take out and what it may put in. */
public interface ItemPort {

    /** The stack this face is willing to hand out right now, or null. */
    Stack offer();

    /** The transporter took {@code count} of the offered stack. */
    void taken(int count);

    /** Puts items in; returns what did not fit. */
    Stack insert(Stack stack);
}
