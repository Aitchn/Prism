package com.aitchn.validation.botania.logic;

/** Something that can be filled with mana by flowers and bursts. */
public sealed interface ManaCollector extends BlockEntity permits Pool, Spreader {

    int mana();

    int capacity();

    /** Adds up to {@code amount}; returns how much was accepted. */
    int receive(int amount);
}
