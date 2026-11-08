package com.aitchn.prism.core.look;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class LookPoolTest {

    private static final ResolvedPool POOL = ResolvedPool.standard();

    @Test
    void slotCountsPerClass() {
        Map<String, Integer> perClass = new TreeMap<>();
        for (ResolvedPool.Family family : POOL.families()) {
            perClass.merge(family.family().physics().id(), family.slots().size(), Integer::sum);
        }
        // 16 instruments x 25 notes x 2 minus the plain state, minus the reserved shell
        assertEquals(798, perClass.get("full/opaque/wood"));
        assertEquals(15, perClass.get("full/opaque/grass"));
        assertEquals(12, perClass.get("full/opaque/stone"));
        // 13 per leaves block, minus the shell on spruce
        assertEquals(25, perClass.get("full/clear/grass"));
        assertEquals(63, perClass.get("none/clear/stone"));
        assertEquals(14, perClass.get("none/clear/grass"));
    }

    @Test
    void noStateIsHandedOutTwiceOrShared() {
        Set<StateRef> seen = new HashSet<>();
        for (ResolvedPool.Family family : POOL.families()) {
            for (StateRef state : family.slots()) {
                assertTrue(seen.add(state), "duplicate " + state);
            }
            family.shell().ifPresent(shell -> assertTrue(seen.add(shell), "shell is also a slot " + shell));
        }
    }

    @Test
    void borrowedStatesAreNeverTheirOwnCanonicalForm() {
        for (ResolvedPool.Family family : POOL.families()) {
            Set<StateRef> existing = new HashSet<>(family.allStates());
            List<StateRef> all = family.slots();
            for (StateRef state : all) {
                StateRef canonical = family.family().canonical(state);
                assertNotEquals(state, canonical);
                assertTrue(existing.contains(canonical), canonical + " does not exist");
                // a canonical state is itself left alone
                assertEquals(canonical, family.family().canonical(canonical));
                assertTrue(family.slots().stream().noneMatch(canonical::equals), "canonical form is borrowable");
            }
        }
    }

    @Test
    void everyRealStateOfAFamilyMapsToAStateThatIsNotBorrowed() {
        for (ResolvedPool.Family family : POOL.families()) {
            Set<StateRef> borrowed = new HashSet<>(family.slots());
            family.shell().ifPresent(borrowed::add);
            for (StateRef real : family.allStates()) {
                StateRef shown = family.family().canonical(real);
                if (!borrowed.contains(real)) {
                    // a state nobody borrows may only change into another state nobody borrows
                    assertFalse(borrowed.contains(shown), real + " would be shown as borrowed " + shown);
                }
            }
        }
    }

    @Test
    void noteBlockCanonicalisesEveryInstrumentIncludingTheOnesPrismNeverBorrows() {
        BorrowedFamily note = LookPool.family("minecraft:note_block").orElseThrow();
        StateRef harp = StateRef.parse("minecraft:note_block[instrument=harp,note=0,powered=false]");
        assertEquals(harp, note.canonical(StateRef.parse("minecraft:note_block[instrument=trumpet,note=17,powered=true]")));
        assertEquals(harp, note.canonical(StateRef.parse("minecraft:note_block[instrument=custom_head,note=3,powered=false]")));
        assertEquals(harp, note.canonical(harp));
    }

    @Test
    void leavesKeepWaterloggedStatesAsTheyAre() {
        BorrowedFamily leaves = LookPool.family("minecraft:spruce_leaves").orElseThrow();
        StateRef wet = StateRef.parse("minecraft:spruce_leaves[distance=2,persistent=true,waterlogged=true]");
        assertEquals(wet, leaves.canonical(wet));
        assertEquals(StateRef.parse("minecraft:spruce_leaves[distance=7,persistent=false,waterlogged=false]"),
                leaves.canonical(StateRef.parse("minecraft:spruce_leaves[distance=2,persistent=true,waterlogged=false]")));
    }

    @Test
    void tripwireCanonicalisesDisarmedAndPoweredButKeepsSidesAndAttached() {
        BorrowedFamily tripwire = LookPool.family("minecraft:tripwire").orElseThrow();
        assertEquals(StateRef.parse(
                "minecraft:tripwire[attached=true,disarmed=false,east=true,north=false,powered=false,south=false,west=false]"),
                tripwire.canonical(StateRef.parse(
                        "minecraft:tripwire[attached=true,disarmed=true,east=true,north=false,powered=true,south=false,west=false]")));
        // fewest sides first
        List<StateRef> slots = POOL.family("minecraft:tripwire").orElseThrow().slots();
        assertEquals("false", slots.getFirst().properties().get("east"));
        assertEquals("false", slots.getFirst().properties().get("west"));
    }

    @Test
    void vanillaModelsFollowTheGameBlockstateFiles() {
        BorrowedFamily dispenser = LookPool.family("minecraft:dispenser").orElseThrow();
        assertEquals(new ModelRef("minecraft:block/dispenser", 0, 90),
                dispenser.vanillaModel(StateRef.parse("minecraft:dispenser[facing=east,triggered=false]")));
        assertEquals(new ModelRef("minecraft:block/dispenser_vertical", 180, 0),
                dispenser.vanillaModel(StateRef.parse("minecraft:dispenser[facing=down,triggered=true]")));
        assertEquals(new ModelRef("minecraft:block/dispenser_vertical"),
                dispenser.vanillaModel(StateRef.parse("minecraft:dispenser[facing=up,triggered=true]")));
    }

    @Test
    void stateRefRoundTrips() {
        StateRef state = StateRef.parse("note_block[powered=true,instrument=bass,note=3]");
        assertEquals("minecraft:note_block[instrument=bass,note=3,powered=true]", state.blockData());
        assertEquals(state, StateRef.parse(state.blockData()));
        assertEquals("minecraft:structure_void", StateRef.parse("structure_void").blockData());
    }

    @Test
    void aFamilyThatDoesNotExistOnTheServerIsDroppedWithAWarning() {
        ResolvedPool older = ResolvedPool.resolve(family -> family.block().equals("minecraft:target")
                ? List.of() : LookPool.defaultStates(family));
        assertTrue(older.family("minecraft:target").isEmpty());
        assertTrue(older.warnings().stream().anyMatch(w -> w.contains("minecraft:target")));
    }

    @Test
    void aStateTheServerLacksIsNotOffered() {
        ResolvedPool fewer = ResolvedPool.resolve(family -> family.block().equals("minecraft:sugar_cane")
                ? LookPool.defaultStates(family).stream().filter(s -> Integer.parseInt(s.properties().get("age")) < 8).toList()
                : LookPool.defaultStates(family));
        // age=15 (the shell) is gone, so the family has no shell, and only ages 1 to 7 are offered
        ResolvedPool.Family cane = fewer.family("minecraft:sugar_cane").orElseThrow();
        assertEquals(7, cane.slots().size());
        assertEquals(Optional.empty(), cane.shell());
    }
}
