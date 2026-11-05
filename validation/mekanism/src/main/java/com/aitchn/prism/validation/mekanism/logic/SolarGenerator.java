package com.aitchn.prism.validation.mekanism.logic;

import java.util.function.BooleanSupplier;

/** Produces energy while the sky is bright. The "is day" check is injected so the logic stays testable. */
public final class SolarGenerator {

    public static final long OUTPUT_PER_TICK = 50;

    private final EnergyBuffer buffer = new EnergyBuffer(96_000, 0, 1_000);
    private final BooleanSupplier sunlit;

    public SolarGenerator(BooleanSupplier sunlit) {
        this.sunlit = sunlit;
    }

    public EnergyBuffer energy() {
        return buffer;
    }

    public void tick() {
        if (sunlit.getAsBoolean()) {
            buffer.produce(OUTPUT_PER_TICK);
        }
    }
}
