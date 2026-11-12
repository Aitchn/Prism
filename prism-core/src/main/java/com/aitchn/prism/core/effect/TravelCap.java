package com.aitchn.prism.core.effect;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/**
 * The per-player cap on travelling visuals ({@code effects.travel.max-per-player}, ADR-0027): when a player would see
 * more than the cap, the farthest go. Pure function.
 */
public final class TravelCap {

    private TravelCap() {}

    /**
     * Which travels to take away from a player who sees {@code distanceSq.length} of them with a cap of {@code cap}.
     *
     * @param distanceSq the squared distance from the player to each travel's current position
     * @return the indexes to drop, farthest first; empty when the count is within the cap. Ties go to the higher index
     *         (the newer travel), so a burst of equal launches keeps the older ones
     */
    public static int @NotNull [] drop(double @NotNull [] distanceSq, int cap) {
        int excess = distanceSq.length - Math.max(0, cap);
        if (excess <= 0) {
            return new int[0];
        }
        Integer[] order = new Integer[distanceSq.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> distanceSq[a] != distanceSq[b] ? Double.compare(distanceSq[b], distanceSq[a])
                : Integer.compare(b, a));
        int[] dropped = new int[excess];
        for (int i = 0; i < excess; i++) {
            dropped[i] = order[i];
        }
        return dropped;
    }
}
