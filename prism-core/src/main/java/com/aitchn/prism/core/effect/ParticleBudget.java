package com.aitchn.prism.core.effect;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/**
 * Which block emitters a player is sent in one cycle of the effects clock (ADR-0027): those in range, nearest first,
 * until the player's particle budget for the cycle is used up. The first emission that does not fit ends the cycle for
 * that player, so everything a player sees is nearer than everything it does not. Pure functions.
 */
public final class ParticleBudget {

    private ParticleBudget() {}

    /** The particles one player may be sent per cycle, from the per-second budget and the clock interval. */
    public static int perCycle(int perSecond, int interval) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) perSecond * Math.max(1, interval) / 20));
    }

    /**
     * Selects emitters for a viewer at {@code (vx, vy, vz)}.
     *
     * @param x       the points the emitters fire from
     * @param rangeSq the squared range of each emitter: a viewer farther away gets nothing from it
     * @param cost    the particles each emitter sends this cycle (emissions times count); 0 skips it
     * @param budget  the most particles the viewer may get this cycle
     * @return the indexes of the chosen emitters, nearest first
     */
    public static int @NotNull [] select(double vx, double vy, double vz, double @NotNull [] x, double @NotNull [] y,
            double @NotNull [] z, double @NotNull [] rangeSq, int @NotNull [] cost, int budget) {
        int n = x.length;
        int[] candidates = new int[n];
        double[] distances = new double[n];
        int found = 0;
        long total = 0;
        for (int i = 0; i < n; i++) {
            if (cost[i] <= 0) {
                continue;
            }
            double dx = x[i] - vx;
            double dy = y[i] - vy;
            double dz = z[i] - vz;
            double d = dx * dx + dy * dy + dz * dz;
            if (d > rangeSq[i]) {
                continue;
            }
            candidates[found] = i;
            distances[i] = d;
            found++;
            total += cost[i];
        }
        if (total <= budget) {
            int[] all = Arrays.copyOf(candidates, found);
            return sortByDistance(all, distances);
        }
        int[] ordered = sortByDistance(Arrays.copyOf(candidates, found), distances);
        int taken = 0;
        long left = Math.max(0, budget);
        for (int i : ordered) {
            if (cost[i] > left) {
                break;
            }
            left -= cost[i];
            taken++;
        }
        return Arrays.copyOf(ordered, taken);
    }

    private static int[] sortByDistance(int[] indexes, double[] distances) {
        Integer[] boxed = new Integer[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
            boxed[i] = indexes[i];
        }
        Arrays.sort(boxed, (a, b) -> distances[a] != distances[b] ? Double.compare(distances[a], distances[b])
                : Integer.compare(a, b));
        int[] sorted = new int[boxed.length];
        for (int i = 0; i < boxed.length; i++) {
            sorted[i] = boxed[i];
        }
        return sorted;
    }
}
