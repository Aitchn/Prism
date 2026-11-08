package com.aitchn.prism.core.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SyntheticIdsTest {

    @Test
    void idsAreConsecutiveFromTheBaseInLookIdOrder() {
        SyntheticIds.Plan plan = SyntheticIds.plan(List.of("m:b", "m:a#x", "m:a", "m:c@east"), 30000);
        assertEquals(List.of("m:a", "m:a#x", "m:b", "m:c@east"), List.copyOf(plan.ids().keySet()));
        assertEquals(30000, plan.idOf("m:a").getAsInt());
        assertEquals(30001, plan.idOf("m:a#x").getAsInt());
        assertEquals(30003, plan.idOf("m:c@east").getAsInt());
        assertFalse(plan.idOf("m:missing").isPresent());
        assertEquals(30000, plan.base());
        assertEquals(30004, plan.stateCount());
    }

    @Test
    void sameContentGivesTheSameIdsWhateverOrderItArrivesIn() {
        List<String> looks = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            looks.add("synth:b" + i + (i % 3 == 0 ? "#on" : "") + (i % 5 == 0 ? "@east" : ""));
        }
        SyntheticIds.Plan first = SyntheticIds.plan(looks, 33000);
        Collections.shuffle(looks, new Random(7));
        looks.addAll(looks.subList(0, 100)); // duplicates are one look
        SyntheticIds.Plan second = SyntheticIds.plan(looks, 33000);
        assertEquals(first, second);
        assertEquals(2000, first.size());
    }

    @Test
    void everyIdIsUniqueAndAboveTheBase() {
        List<String> looks = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            looks.add("synth:b" + i);
        }
        int base = SyntheticIds.defaultBase(29_000);
        SyntheticIds.Plan plan = SyntheticIds.plan(looks, base);
        Set<Integer> seen = new HashSet<>(plan.ids().values());
        assertEquals(10_000, seen.size());
        assertTrue(seen.stream().allMatch(id -> id >= base), "ids start after the base");
        assertEquals(base + 10_000, plan.stateCount());
    }

    @Test
    void defaultBaseLeavesAMarginAboveTheVanillaStates() {
        assertEquals(29_000 + SyntheticIds.DEFAULT_MARGIN, SyntheticIds.defaultBase(29_000));
    }

    @Test
    void globalBitsFollowTheRegistrySizeGeyserWillHave() {
        assertEquals(1, SyntheticIds.bitsFor(1));
        assertEquals(1, SyntheticIds.bitsFor(2));
        assertEquals(2, SyntheticIds.bitsFor(3));
        assertEquals(15, SyntheticIds.bitsFor(32768));
        assertEquals(16, SyntheticIds.bitsFor(32769));
        assertEquals(16, SyntheticIds.bitsFor(65536));
        assertEquals(17, SyntheticIds.bitsFor(65537));
        // 10,000 looks above a 29,000 state vanilla range: 29,000 + 4096 margin + 10,000 = 43,096 states, 16 bits
        assertEquals(16, SyntheticIds.plan(manyLooks(10_000), SyntheticIds.defaultBase(29_000)).globalBits());
        // more looks than a 16 bit palette holds still get an exact width
        assertEquals(17, SyntheticIds.plan(manyLooks(40_000), SyntheticIds.defaultBase(29_000)).globalBits());
    }

    @Test
    void anEmptyPlanHasNoStatesOfItsOwn() {
        SyntheticIds.Plan plan = SyntheticIds.Plan.empty(33000);
        assertEquals(0, plan.size());
        assertEquals(33000, plan.stateCount());
    }

    @Test
    void idsThatOverflowAnIntAreRefused() {
        assertThrows(ArithmeticException.class, () -> SyntheticIds.plan(List.of("a", "b"), Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> SyntheticIds.plan(List.of("a"), -1));
    }

    private static List<String> manyLooks(int count) {
        List<String> looks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            looks.add("synth:b" + i);
        }
        return looks;
    }
}
