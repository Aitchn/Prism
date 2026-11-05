package com.aitchn.prism.validation.mekanism.logic;

/** Produces energy while the sky is bright. The adapter tells it whether the sun is up, so the logic stays testable. */
public final class SolarGenerator {

    public static final long OUTPUT_PER_TICK = 50;

    private final EnergyBuffer buffer = new EnergyBuffer(96_000, 0, 1_000);
    private boolean sunlit;

    public EnergyBuffer energy() {
        return buffer;
    }

    /** Whether the last tick was sunlit, for status displays. */
    public boolean sunlit() {
        return sunlit;
    }

    public void tick(boolean sunlitNow) {
        sunlit = sunlitNow;
        if (sunlitNow) {
            buffer.produce(OUTPUT_PER_TICK);
        }
    }
}
