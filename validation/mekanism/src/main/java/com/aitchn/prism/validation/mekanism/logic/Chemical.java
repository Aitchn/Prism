package com.aitchn.prism.validation.mekanism.logic;

/**
 * Gases and (for lack of a fluid API in Prism, and because Prism must not ship one) water. {@code id} is the
 * registry-like name used in recipe files. Amounts are always millibuckets (mB).
 */
public record Chemical(String id) {

    public static final Chemical WATER = new Chemical("water");
    public static final Chemical HYDROGEN = new Chemical("hydrogen");
    public static final Chemical OXYGEN = new Chemical("oxygen");
}
