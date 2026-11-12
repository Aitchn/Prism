package com.aitchn.prism.core.effect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EffectMathTest {

    // ---------------------------------------------------------------- emitter clock

    @Test
    void ratesBelowOnePerCycleFireEveryFewCycles() {
        double perCycle = EmitterClock.perCycle(0.5, 10); // one every two seconds, clock every half second
        assertEquals(0.25, perCycle, 1e-12);
        int total = 0;
        int busiest = 0;
        for (long cycle = 0; cycle < 400; cycle++) {
            int n = EmitterClock.emissions(cycle, perCycle, 0.3);
            total += n;
            busiest = Math.max(busiest, n);
        }
        assertEquals(100, total, 1);
        assertEquals(1, busiest);
    }

    @Test
    void ratesAboveOnePerCycleFireSeveralTimesAndKeepTheRate() {
        double perCycle = EmitterClock.perCycle(10, 5); // 2.5 per cycle
        int total = 0;
        for (long cycle = 0; cycle < 1000; cycle++) {
            int n = EmitterClock.emissions(cycle, perCycle, 0.7);
            assertTrue(n == 2 || n == 3, "got " + n);
            total += n;
        }
        assertEquals(2500, total, 1);
    }

    @Test
    void phasesSpreadIdenticalEmittersAndAreStable() {
        assertEquals(EmitterClock.phase(10, 64, -3, 0), EmitterClock.phase(10, 64, -3, 0));
        int firstCycle = 0;
        for (int x = 0; x < 100; x++) {
            double phase = EmitterClock.phase(x, 64, 0, 0);
            assertTrue(phase >= 0 && phase < 1);
            firstCycle += EmitterClock.emissions(0, 0.25, phase);
        }
        // A quarter of a hundred emitters are due in any one cycle, not all or none.
        assertTrue(firstCycle > 10 && firstCycle < 40, "due in cycle 0: " + firstCycle);
    }

    @Test
    void noRateNoEmission() {
        assertEquals(0, EmitterClock.emissions(5, 0, 0.5));
    }

    // ---------------------------------------------------------------- particle budget

    @Test
    void everythingInRangeFitsWhenUnderBudget() {
        double[] x = {0, 5, 40};
        int[] chosen = ParticleBudget.select(0, 0, 0, x, new double[3], new double[3],
                new double[] {100, 100, 100}, new int[] {1, 1, 1}, 10);
        assertArrayEquals(new int[] {0, 1}, chosen); // the third is out of range (40 > 10)
    }

    @Test
    void overBudgetTakesNearestFirstAndStopsAtTheFirstMiss() {
        double[] x = {9, 1, 5, 3};
        double[] rangeSq = {1000, 1000, 1000, 1000};
        int[] cost = {1, 2, 4, 1};
        int[] chosen = ParticleBudget.select(0, 0, 0, x, new double[4], new double[4], rangeSq, cost, 4);
        // nearest first: 1 (2), 3 (1) = 3; then 2 costs 4 > 1 left: stop, so 0 is not taken although it would fit
        assertArrayEquals(new int[] {1, 3}, chosen);
    }

    @Test
    void zeroCostIsSkippedAndBudgetPerCycleFollowsTheInterval() {
        int[] chosen = ParticleBudget.select(0, 0, 0, new double[] {1, 2}, new double[2], new double[2],
                new double[] {9, 9}, new int[] {0, 1}, 5);
        assertArrayEquals(new int[] {1}, chosen);
        assertEquals(500, ParticleBudget.perCycle(2000, 5));
        assertEquals(0, ParticleBudget.perCycle(0, 5));
    }

    // ---------------------------------------------------------------- travel path

    @Test
    void pathInterpolatesAndClamps() {
        TravelPath path = new TravelPath(0, 64, 0, 10, 64, -20, 40);
        assertArrayEquals(new double[] {0, 64, 0}, path.at(0), 1e-12);
        assertArrayEquals(new double[] {5, 64, -10}, path.at(20), 1e-12);
        assertArrayEquals(new double[] {10, 64, -20}, path.at(40), 1e-12);
        assertArrayEquals(new double[] {10, 64, -20}, path.at(99), 1e-12);
        assertArrayEquals(new double[] {0, 64, 0}, path.at(-5), 1e-12);
        assertTrue(path.arrived(40));
        assertTrue(!path.arrived(39));
    }

    @Test
    void movesAreAtMostFiftyNineTicks() {
        TravelPath shortOne = new TravelPath(0, 0, 0, 1, 0, 0, 30);
        assertEquals(30, shortOne.moveTarget(0));
        assertEquals(29, shortOne.moveDuration(1));
        TravelPath longOne = new TravelPath(0, 0, 0, 1, 0, 0, 200);
        int now = 1;
        int moves = 0;
        while (!longOne.arrived(now)) {
            int target = longOne.moveTarget(now);
            assertTrue(target - now <= TravelPath.MAX_STEP);
            assertEquals(target - now, longOne.moveDuration(now));
            now = target;
            moves++;
        }
        assertEquals(4, moves); // 1->60->119->178->200
    }

    @Test
    void trailPointsCoverTheStretchOfOneTick() {
        TravelPath path = new TravelPath(0, 0, 0, 10, 0, 0, 10);
        double[] points = path.trail(3, 2);
        assertArrayEquals(new double[] {2.5, 0, 0, 3, 0, 0}, points, 1e-12);
        assertEquals(3, path.trail(1, 1).length);
    }

    @Test
    void distanceToPathUsesTheNearestPoint() {
        TravelPath path = new TravelPath(0, 0, 0, 10, 0, 0, 10);
        assertEquals(9, path.distanceSq(5, 3, 0), 1e-12);
        assertEquals(4, path.distanceSq(-2, 0, 0), 1e-12);
        assertEquals(1, path.distanceSq(11, 0, 0), 1e-12);
        assertEquals(2, TravelPath.beamPoints(0, 4, 512));
        assertEquals(41, TravelPath.beamPoints(10, 4, 512));
        assertEquals(512, TravelPath.beamPoints(1000, 4, 512));
    }

    // ---------------------------------------------------------------- travel cap

    @Test
    void capDropsTheFarthest() {
        assertArrayEquals(new int[0], TravelCap.drop(new double[] {1, 2}, 2));
        assertArrayEquals(new int[] {2, 0}, TravelCap.drop(new double[] {50, 1, 99, 3}, 2));
        // equal distances: the newer (higher index) goes first
        assertArrayEquals(new int[] {2}, TravelCap.drop(new double[] {4, 4, 4}, 2));
        assertArrayEquals(new int[] {0}, TravelCap.drop(new double[] {4}, 0));
    }
}
