package com.aitchn.prism.validation.mekanism.logic;

/**
 * Gases, infusions and (here, for lack of a fluid API in Prism) water. {@code id} is the registry-like name.
 * Amounts are always millibuckets (mB).
 */
public record Chemical(String id) {

    public static final Chemical WATER = new Chemical("water");
    public static final Chemical HYDROGEN = new Chemical("hydrogen");
    public static final Chemical OXYGEN = new Chemical("oxygen");
    public static final Chemical CHLORINE = new Chemical("chlorine");
    public static final Chemical HYDROGEN_CHLORIDE = new Chemical("hydrogen_chloride");
    public static final Chemical SULFUR_DIOXIDE = new Chemical("sulfur_dioxide");
}
