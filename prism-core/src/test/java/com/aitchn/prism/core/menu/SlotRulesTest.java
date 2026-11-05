package com.aitchn.prism.core.menu;

import static com.aitchn.prism.core.menu.SlotRules.Action.*;
import static com.aitchn.prism.core.menu.SlotRules.Outcome.*;
import static com.aitchn.prism.core.menu.SlotRules.Role.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SlotRulesTest {

    @Test
    void buttonsNeverMoveItems() {
        for (SlotRules.Action action : SlotRules.Action.values()) {
            assertEquals(DENY, SlotRules.decide(BUTTON, action, true, false), action.name());
        }
    }

    @Test
    void inputsAcceptOnlyWhatTheMenuAccepts() {
        assertEquals(ALLOW, SlotRules.decide(INPUT, PUT, true, false));
        assertEquals(DENY, SlotRules.decide(INPUT, PUT, false, false));
        assertEquals(DENY, SlotRules.decide(INPUT, SWAP_CURSOR, false, false));
        assertEquals(ALLOW, SlotRules.decide(INPUT, TAKE, false, true));
        assertEquals(ALLOW, SlotRules.decide(INPUT, HOTBAR_SWAP, false, true));
        assertEquals(DENY, SlotRules.decide(INPUT, HOTBAR_SWAP, false, false));
        assertEquals(DENY, SlotRules.decide(INPUT, COLLECT, true, false));
    }

    @Test
    void outputsAreTakeOnly() {
        assertEquals(ALLOW, SlotRules.decide(OUTPUT, TAKE, false, true));
        assertEquals(ALLOW, SlotRules.decide(OUTPUT, SHIFT_OUT, false, true));
        assertEquals(DENY, SlotRules.decide(OUTPUT, PUT, true, false));
        assertEquals(DENY, SlotRules.decide(OUTPUT, SWAP_CURSOR, true, false));
        assertEquals(ALLOW, SlotRules.decide(OUTPUT, HOTBAR_SWAP, true, true));
        assertEquals(DENY, SlotRules.decide(OUTPUT, HOTBAR_SWAP, true, false));
    }

    @Test
    void playerInventoryClicks() {
        assertEquals(INSERT, SlotRules.decide(null, SHIFT_IN, true, false));
        assertEquals(DENY, SlotRules.decide(null, COLLECT, true, false));
        assertEquals(ALLOW, SlotRules.decide(null, TAKE, true, false));
    }

    @Test
    void drags() {
        assertTrue(SlotRules.allowDrag(List.of(INPUT, INPUT), true));
        assertFalse(SlotRules.allowDrag(List.of(INPUT, OUTPUT), true));
        assertFalse(SlotRules.allowDrag(List.of(INPUT), false));
        assertTrue(SlotRules.allowDrag(List.of(), false));
    }
}
