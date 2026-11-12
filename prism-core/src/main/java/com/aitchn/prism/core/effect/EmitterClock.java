package com.aitchn.prism.core.effect;

/**
 * When a block's emitter fires (ADR-0027). Emitters are not timers: the effects clock runs every
 * {@code effects.particles.interval} ticks for all of them, and this decides how many emissions an emitter owes in
 * that cycle, without any state per emitter.
 *
 * <p>An emitter of {@code rate} per second owes {@code perCycle = rate * interval / 20} emissions per cycle. Cycle
 * {@code c} emits {@code floor((c + 1) * perCycle + phase) - floor(c * perCycle + phase)}, so over any {@code n}
 * cycles the total is within one of {@code n * perCycle}: a rate below one cycle (0.2 per second) fires every few
 * cycles, a rate above fires several times in one. The phase, taken from the block position, shifts each emitter so a
 * wall of identical emitters does not fire in step.
 */
public final class EmitterClock {

    private EmitterClock() {}

    /** Emissions per cycle of an emitter of {@code ratePerSecond}, with the clock running every {@code interval} ticks. */
    public static double perCycle(double ratePerSecond, int interval) {
        return ratePerSecond * Math.max(1, interval) / 20.0;
    }

    /** How many emissions are due in {@code cycle}; 0 or more. */
    public static int emissions(long cycle, double perCycle, double phase) {
        if (!(perCycle > 0)) {
            return 0;
        }
        double start = Math.floor(cycle * perCycle + phase);
        double end = Math.floor((cycle + 1) * perCycle + phase);
        return (int) Math.max(0, end - start);
    }

    /** A phase in [0, 1) from a block position and an emitter index: the same block always gets the same phase. */
    public static double phase(int x, int y, int z, int emitter) {
        long h = x * 0x9E3779B97F4A7C15L;
        h ^= (y + 0x632BE59BD9B4E019L) * 0xC2B2AE3D27D4EB4FL;
        h ^= (z + 0x165667B19E3779F9L) * 0x94D049BB133111EBL;
        h ^= emitter * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 31;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }
}
