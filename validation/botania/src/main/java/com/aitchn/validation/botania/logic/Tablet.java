package com.aitchn.validation.botania.logic;

/** Mana tablet: an item that stores mana in its own stack data. Moves mana to and from a pool. */
public final class Tablet {

    public static final int CAPACITY = 500_000;
    public static final int STEP = 1000;

    private Tablet() {}

    /** Mana a click moves from the pool into the tablet (never more than either side allows). */
    public static int toTablet(int poolMana, int tabletMana) {
        return Math.max(0, Math.min(STEP, Math.min(poolMana, CAPACITY - tabletMana)));
    }

    /** Mana a click moves from the tablet into the pool. */
    public static int toPool(int tabletMana, int poolRoom) {
        return Math.max(0, Math.min(STEP, Math.min(tabletMana, poolRoom)));
    }
}
