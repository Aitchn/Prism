package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SlotsTest {

    private static Stack s(String id, int n) {
        return new Stack(id, n);
    }

    @Test
    void addingToAnEmptySlotFillsTheMachine() {
        assertEquals(s("a", 5), Slots.reconcileInput(null, null, s("a", 5)));
    }

    @Test
    void addingMoreOfTheSameItemAddsTheDelta() {
        assertEquals(s("a", 8), Slots.reconcileInput(s("a", 5), s("a", 5), s("a", 8)));
    }

    @Test
    void theMachineConsumingWhileThePlayerAddsKeepsBothChanges() {
        // shown 5, the machine used 1 (now 4), the player added 3 (slot shows 8)
        assertEquals(s("a", 7), Slots.reconcileInput(s("a", 5), s("a", 4), s("a", 8)));
    }

    @Test
    void takingEverythingEmptiesTheSlotButKeepsWhatTheMachineAddedMeanwhile() {
        assertNull(Slots.reconcileInput(s("a", 5), s("a", 5), null));
        assertEquals(s("a", 2), Slots.reconcileInput(s("a", 5), s("a", 7), null));
    }

    @Test
    void takingMoreThanTheMachineStillHasEmptiesItWithoutGoingNegative() {
        assertNull(Slots.reconcileInput(s("a", 5), s("a", 3), null));
        assertNull(Slots.reconcileInput(s("a", 5), null, s("a", 2)));
    }

    @Test
    void swappingToAnotherItemReplacesTheSlot() {
        assertEquals(s("b", 2), Slots.reconcileInput(s("a", 5), s("a", 5), s("b", 2)));
    }

    @Test
    void inputNeverExceedsAStack() {
        assertEquals(s("a", Stack.MAX), Slots.reconcileInput(s("a", 60), s("a", 60), s("a", 64 + 6)));
    }

    @Test
    void outputLosesOnlyWhatThePlayerTook() {
        assertEquals(s("o", 5), Slots.reconcileOutput(s("o", 8), s("o", 10), s("o", 3)));
        assertNull(Slots.reconcileOutput(s("o", 8), s("o", 8), null));
        assertEquals(s("o", 2), Slots.reconcileOutput(s("o", 8), s("o", 10), null));
    }

    @Test
    void outputUnchangedWhenNothingWasTaken() {
        assertEquals(s("o", 9), Slots.reconcileOutput(s("o", 8), s("o", 9), s("o", 8)));
        assertNull(Slots.reconcileOutput(null, null, null));
    }
}
