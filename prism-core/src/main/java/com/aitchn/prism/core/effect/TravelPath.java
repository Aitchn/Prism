package com.aitchn.prism.core.effect;

import org.jetbrains.annotations.NotNull;

/**
 * The straight path of a travelling visual (ADR-0027): where it is at a tick, when the client must be told to move it
 * next, and which points of the trail belong to one tick.
 *
 * <p>The model is an item display that the client interpolates between teleports. A display entity's
 * position interpolation lasts at most {@link #MAX_STEP} ticks, so a flight is sent as moves of at most that length:
 * at tick {@code t} the client is told to move to where the travel is at {@code min(t + MAX_STEP, ticks)}, over that
 * many ticks. A two-second flight is one move; a minute is 21.
 */
public record TravelPath(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, int ticks) {

    /** The longest interpolation of a display entity's position the client accepts, in ticks. */
    public static final int MAX_STEP = 59;

    public TravelPath {
        if (ticks < 1) {
            throw new IllegalArgumentException("ticks must be at least 1");
        }
    }

    /** The point at {@code tick} (fractions allowed), clamped to the ends. */
    public double @NotNull [] at(double tick) {
        double f = Math.max(0, Math.min(1, tick / ticks));
        return new double[] {fromX + (toX - fromX) * f, fromY + (toY - fromY) * f, fromZ + (toZ - fromZ) * f};
    }

    /** Whether the travel is over at {@code tick}. */
    public boolean arrived(int tick) {
        return tick >= ticks;
    }

    /** The tick the move sent at {@code now} ends at: where the client is moving the model to. */
    public int moveTarget(int now) {
        return Math.min(ticks, Math.max(0, now) + MAX_STEP);
    }

    /** The interpolation length of the move sent at {@code now}, in ticks (at least 1). */
    public int moveDuration(int now) {
        return Math.max(1, moveTarget(now) - Math.max(0, now));
    }

    /**
     * The trail points of tick {@code tick}: {@code points} points spread evenly over the stretch flown from
     * {@code tick - 1} to {@code tick}, ending at {@code tick}. Returned as x, y, z triples.
     */
    public double @NotNull [] trail(int tick, int points) {
        int n = Math.max(1, points);
        double[] out = new double[n * 3];
        for (int i = 0; i < n; i++) {
            double[] p = at(tick - 1 + (i + 1) / (double) n);
            out[i * 3] = p[0];
            out[i * 3 + 1] = p[1];
            out[i * 3 + 2] = p[2];
        }
        return out;
    }

    /** The squared distance from {@code (x, y, z)} to the nearest point of the whole path. */
    public double distanceSq(double x, double y, double z) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double length = dx * dx + dy * dy + dz * dz;
        double f = length == 0 ? 0 : ((x - fromX) * dx + (y - fromY) * dy + (z - fromZ) * dz) / length;
        f = Math.max(0, Math.min(1, f));
        double px = fromX + dx * f - x;
        double py = fromY + dy * f - y;
        double pz = fromZ + dz * f - z;
        return px * px + py * py + pz * pz;
    }

    /** How many points a beam of {@code density} points per block gets, at least 2 and at most {@code max}. */
    public static int beamPoints(double length, double density, int max) {
        if (!(density > 0) || !Double.isFinite(length)) {
            return 2;
        }
        double points = Math.ceil(length * density) + 1;
        return (int) Math.max(2, Math.min(max, points));
    }
}
