package com.aitchn.prism.core.effect;

import com.aitchn.prism.api.effect.Emitter;
import com.aitchn.prism.api.effect.ParticleSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * The block emitters of one chunk (ADR-0027), as an immutable snapshot: one entry per running emitter of every
 * emitting block, with the point it fires from, its squared range, its emissions per cycle and its phase. Built on the
 * chunk's thread when a block is placed, removed or changes variant, or the chunk loads; read by the effects clock
 * from any thread. No object per block.
 */
public final class EmitterSet {

    /** A chunk without emitters. */
    public static final EmitterSet EMPTY = new EmitterSet(new double[0], new double[0], new double[0], new double[0],
            new double[0], new double[0], new int[0], List.of());

    private final double[] x;
    private final double[] y;
    private final double[] z;
    private final double[] rangeSq;
    private final double[] perCycle;
    private final double[] phase;
    private final int[] spec;
    private final List<ParticleSpec> specs;

    private EmitterSet(double[] x, double[] y, double[] z, double[] rangeSq, double[] perCycle, double[] phase,
            int[] spec, List<ParticleSpec> specs) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.rangeSq = rangeSq;
        this.perCycle = perCycle;
        this.phase = phase;
        this.spec = spec;
        this.specs = specs;
    }

    public int size() {
        return x.length;
    }

    public boolean isEmpty() {
        return x.length == 0;
    }

    /** The largest range of any emitter here, in blocks. */
    public double maxRange() {
        double max = 0;
        for (double r : rangeSq) {
            max = Math.max(max, r);
        }
        return Math.sqrt(max);
    }

    public double x(int i) {
        return x[i];
    }

    public double y(int i) {
        return y[i];
    }

    public double z(int i) {
        return z[i];
    }

    /** What entry {@code i} sends per emission. */
    public @NotNull ParticleSpec particle(int i) {
        return specs.get(spec[i]);
    }

    /** How many emissions entry {@code i} owes in {@code cycle}. */
    public int emissions(int i, long cycle) {
        return EmitterClock.emissions(cycle, perCycle[i], phase[i]);
    }

    /** Builds a set from blocks, in any order. */
    public static final class Builder {

        private final int interval;
        private final double defaultRange;
        private final List<double[]> entries = new ArrayList<>();
        private final List<ParticleSpec> specs = new ArrayList<>();
        private final List<Integer> specOf = new ArrayList<>();

        /**
         * @param interval     the effects clock's interval in ticks
         * @param defaultRange the range of an emitter that gives none ({@code effects.particles.range})
         */
        public Builder(int interval, double defaultRange) {
            this.interval = Math.max(1, interval);
            this.defaultRange = defaultRange;
        }

        /**
         * Adds the emitters of the block at {@code (bx, by, bz)} that run in its current variant.
         *
         * @param facing the block's facing ({@code NORTH}, {@code EAST}, {@code SOUTH}, {@code WEST}, or anything else
         *               for no turn): offsets are given for a block facing north
         * @param all    every emitter the block declares, in order; the index of each gives its phase
         */
        public @NotNull Builder add(int bx, int by, int bz, @NotNull String facing, @NotNull List<Emitter> all,
                @NotNull String variant) {
            for (int e = 0; e < all.size(); e++) {
                Emitter emitter = all.get(e);
                if (!emitter.runsIn(variant)) {
                    continue;
                }
                double[] offset = turn(facing, emitter.offsetX(), emitter.offsetZ());
                double range = emitter.range() > 0 ? emitter.range() : defaultRange;
                int index = specs.indexOf(emitter.particle());
                if (index < 0) {
                    index = specs.size();
                    specs.add(emitter.particle());
                }
                specOf.add(index);
                entries.add(new double[] {bx + 0.5 + offset[0], by + 0.5 + emitter.offsetY(), bz + 0.5 + offset[1],
                        range * range, EmitterClock.perCycle(emitter.rate(), interval), EmitterClock.phase(bx, by, bz, e)});
            }
            return this;
        }

        public @NotNull EmitterSet build() {
            int n = entries.size();
            if (n == 0) {
                return EMPTY;
            }
            double[] x = new double[n];
            double[] y = new double[n];
            double[] z = new double[n];
            double[] rangeSq = new double[n];
            double[] perCycle = new double[n];
            double[] phase = new double[n];
            int[] spec = new int[n];
            for (int i = 0; i < n; i++) {
                double[] entry = entries.get(i);
                x[i] = entry[0];
                y[i] = entry[1];
                z[i] = entry[2];
                rangeSq[i] = entry[3];
                perCycle[i] = entry[4];
                phase[i] = entry[5];
                spec[i] = specOf.get(i);
            }
            return new EmitterSet(x, y, z, rangeSq, perCycle, phase, spec, List.copyOf(specs));
        }
    }

    /** Offset {@code (dx, dz)} of a north-facing block, turned to {@code facing}; returns {x, z}. */
    static double[] turn(String facing, double dx, double dz) {
        return switch (facing) {
            case "EAST" -> new double[] {-dz, dx};
            case "SOUTH" -> new double[] {-dx, -dz};
            case "WEST" -> new double[] {dz, -dx};
            default -> new double[] {dx, dz};
        };
    }

    /** One emitter chosen for a viewer: set, entry and how many emissions it sends. */
    public record Pick(@NotNull EmitterSet set, int index, int emissions) {}

    /**
     * What a viewer at {@code (vx, vy, vz)} is sent in {@code cycle} from {@code sets}: the emitters in range that are
     * due, nearest first, within {@code budget} particles ({@link ParticleBudget#select}).
     */
    public static @NotNull List<Pick> select(long cycle, double vx, double vy, double vz,
            @NotNull List<EmitterSet> sets, int budget) {
        int total = 0;
        for (EmitterSet set : sets) {
            total += set.size();
        }
        double[] x = new double[total];
        double[] y = new double[total];
        double[] z = new double[total];
        double[] rangeSq = new double[total];
        int[] cost = new int[total];
        int[] emissions = new int[total];
        int[] setOf = new int[total];
        int[] indexOf = new int[total];
        int at = 0;
        for (int s = 0; s < sets.size(); s++) {
            EmitterSet set = sets.get(s);
            for (int i = 0; i < set.size(); i++) {
                x[at] = set.x[i];
                y[at] = set.y[i];
                z[at] = set.z[i];
                rangeSq[at] = set.rangeSq[i];
                emissions[at] = set.emissions(i, cycle);
                cost[at] = emissions[at] * set.particle(i).count();
                setOf[at] = s;
                indexOf[at] = i;
                at++;
            }
        }
        int[] chosen = ParticleBudget.select(vx, vy, vz, x, y, z, rangeSq, cost, budget);
        List<Pick> picks = new ArrayList<>(chosen.length);
        for (int c : chosen) {
            picks.add(new Pick(sets.get(setOf[c]), indexOf[c], emissions[c]));
        }
        return picks;
    }

    @Override
    public String toString() {
        return "EmitterSet" + Arrays.toString(x);
    }
}
