package com.aitchn.prism.validation.ae2;

/**
 * What an ME energy cell offers to neighbouring blocks through {@link Ae2Content#ENERGY_SINK}. A generator from any
 * mod that knows this key and interface can charge an ME network; Prism only does the lookup.
 */
public interface EnergySink {

    /** Offers {@code ae} energy units; returns how many the network accepted. */
    double insert(double ae);
}
