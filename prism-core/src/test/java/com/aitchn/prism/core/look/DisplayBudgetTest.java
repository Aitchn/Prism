package com.aitchn.prism.core.look;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DisplayBudgetTest {

    @Test
    void nearestChunksTakeTheirWholeNeedAndTheBoundaryChunkTheRest() {
        long[] keys = {1, 2, 3, 4};
        long[] distances = {400, 100, 900, 100};
        int[] needs = {50, 30, 10, 40};
        // order: key 2 (100), key 4 (100, larger key), key 1 (400), key 3 (900)
        assertArrayEquals(new int[] {30, 30, DisplayBudget.NONE, 40}, DisplayBudget.allowances(100, keys, distances,
                needs));
        assertArrayEquals(new int[] {50, 30, 10, 40}, DisplayBudget.allowances(1000, keys, distances, needs));
        assertArrayEquals(new int[] {DisplayBudget.NONE, 30, DisplayBudget.NONE, 40},
                DisplayBudget.allowances(70, keys, distances, needs), "an exactly used budget admits nothing more");
        assertArrayEquals(new int[] {DisplayBudget.NONE, 0, DisplayBudget.NONE, DisplayBudget.NONE},
                DisplayBudget.allowances(0, keys, distances, new int[] {5, 0, 5, 5}),
                "a chunk that needs nothing costs nothing");
    }

    @Test
    void allowancesNeverExceedTheBudget() {
        Random random = new Random(7);
        for (int round = 0; round < 200; round++) {
            int n = random.nextInt(40);
            long[] keys = new long[n];
            long[] distances = new long[n];
            int[] needs = new int[n];
            for (int i = 0; i < n; i++) {
                keys[i] = i;
                distances[i] = random.nextInt(50);
                needs[i] = random.nextInt(300);
            }
            int budget = random.nextInt(3000);
            int sum = 0;
            for (int a : DisplayBudget.allowances(budget, keys, distances, needs)) {
                sum += Math.max(0, a);
            }
            assertTrue(sum <= budget, "sum " + sum + " budget " + budget);
        }
    }

    @Test
    void insideAChunkTheNearestBlocksAreTaken() {
        long[] distances = {50, 10, 30, 20, 40};
        boolean[] chosen = DisplayBudget.nearest(3, distances, new int[] {1, 1, 1, 1, 1});
        assertArrayEquals(new boolean[] {false, true, true, true, false}, chosen);
        // weights: stop at the first that does not fit, so nothing farther sneaks in
        boolean[] weighted = DisplayBudget.nearest(4, distances, new int[] {1, 2, 3, 1, 1});
        assertArrayEquals(new boolean[] {false, true, false, true, false}, weighted);
    }

    /** The converged selection: at most the budget, and nothing hidden is nearer (by the documented rule) than shown. */
    @Test
    void aViewerGetsAtMostTheBudgetAndTheNearest() {
        Random random = new Random(11);
        int chunks = 25;
        int[] cx = new int[chunks];
        int[] cz = new int[chunks];
        int[][] keys = new int[chunks][];
        int[][] weights = new int[chunks][];
        for (int c = 0; c < chunks; c++) {
            cx[c] = c % 5 - 2;
            cz[c] = c / 5 - 2;
            List<Integer> blocks = new ArrayList<>();
            for (int i = 0; i < 80; i++) {
                blocks.add(DisplayHeld.key(random.nextInt(16), 60 + random.nextInt(20), random.nextInt(16)));
            }
            keys[c] = blocks.stream().distinct().mapToInt(Integer::intValue).toArray();
            weights[c] = new int[keys[c].length];
            for (int b = 0; b < keys[c].length; b++) {
                weights[c][b] = random.nextInt(10) == 0 ? 0 : 1; // some enclosed blocks
            }
        }
        int vx = 3;
        int vy = 70;
        int vz = -5;
        for (int budget : new int[] {0, 1, 57, 500, 1000, 100_000}) {
            boolean[][] chosen = DisplayBudget.select(budget, vx, vy, vz, cx, cz, keys, weights);
            int sent = 0;
            long worstShownChunk = Long.MIN_VALUE;
            long bestHiddenChunk = Long.MAX_VALUE;
            for (int c = 0; c < chunks; c++) {
                long d = DisplayBudget.chunkDistanceSq(vx, vz, cx[c], cz[c]);
                for (int b = 0; b < keys[c].length; b++) {
                    if (weights[c][b] == 0) {
                        continue;
                    }
                    if (chosen[c][b]) {
                        sent += weights[c][b];
                        worstShownChunk = Math.max(worstShownChunk, d);
                    } else {
                        bestHiddenChunk = Math.min(bestHiddenChunk, d);
                    }
                }
                // inside a chunk, a hidden block is never nearer than a shown one
                long farthestShown = Long.MIN_VALUE;
                long nearestHidden = Long.MAX_VALUE;
                for (int b = 0; b < keys[c].length; b++) {
                    if (weights[c][b] == 0) {
                        continue;
                    }
                    long bd = DisplayBudget.blockDistanceSq(vx, vy, vz, (cx[c] << 4) + DisplayHeld.keyX(keys[c][b]),
                            DisplayHeld.keyY(keys[c][b]), (cz[c] << 4) + DisplayHeld.keyZ(keys[c][b]));
                    if (chosen[c][b]) {
                        farthestShown = Math.max(farthestShown, bd);
                    } else {
                        nearestHidden = Math.min(nearestHidden, bd);
                    }
                }
                assertTrue(farthestShown <= nearestHidden, "budget " + budget + " chunk " + c);
            }
            assertTrue(sent <= budget, "sent " + sent + " budget " + budget);
            assertTrue(worstShownChunk <= bestHiddenChunk, "a nearer chunk was dropped, budget " + budget);
            if (budget == 100_000) {
                assertEquals(Long.MAX_VALUE, bestHiddenChunk, "everything fits");
            }
        }
    }

    @Test
    void enclosedBlocksAreNeverSent() {
        int[][] keys = {{DisplayHeld.key(0, 64, 0), DisplayHeld.key(1, 64, 0)}};
        int[][] weights = {{0, 1}};
        boolean[][] chosen = DisplayBudget.select(10, 0, 64, 0, new int[] {0}, new int[] {0}, keys, weights);
        int sent = 0;
        for (int b = 0; b < 2; b++) {
            sent += chosen[0][b] ? weights[0][b] : 0;
        }
        assertEquals(1, sent);
        assertFalse(chosen[0][1] && weights[0][1] == 0);
    }

    @Test
    void chunkKeyMatchesBukkit() {
        assertEquals(0x0000000500000003L, DisplayBudget.chunkKey(3, 5));
        assertEquals(0xFFFFFFFEFFFFFFFFL, DisplayBudget.chunkKey(-1, -2));
    }
}
