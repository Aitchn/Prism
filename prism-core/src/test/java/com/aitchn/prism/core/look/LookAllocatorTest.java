package com.aitchn.prism.core.look;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.content.PrismComponents.Render;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LookAllocatorTest {

    private static final ResolvedPool POOL = ResolvedPool.standard();
    private static final PhysicsClass DEFAULT = new PhysicsClass(Collision.FULL, true, SoundGroup.WOOD);

    private static LookRequest request(int i, PhysicsClass physics, Render render) {
        return new LookRequest("test:block_" + String.format("%05d", i), physics, render, false);
    }

    private static List<LookRequest> synthetic(int count) {
        PhysicsClass[] classes = {
            DEFAULT,
            new PhysicsClass(Collision.FULL, true, SoundGroup.STONE),
            new PhysicsClass(Collision.FULL, false, SoundGroup.GRASS),
            new PhysicsClass(Collision.NONE, false, SoundGroup.STONE),
            new PhysicsClass(Collision.NONE, false, SoundGroup.GRASS),
            new PhysicsClass(Collision.FULL, true, SoundGroup.METAL),
            new PhysicsClass(Collision.NONE, false, SoundGroup.WOOD),
        };
        Render[] renders = {Render.AUTO, Render.AUTO, Render.AUTO, Render.STATE, Render.DISPLAY};
        List<LookRequest> requests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            requests.add(request(i, classes[i % classes.length], renders[i % renders.length]));
        }
        return requests;
    }

    @Test
    void tenThousandLooksAllGetARepresentation() {
        LookTable table = LookAllocator.allocate(POOL, synthetic(10_000), false);
        assertEquals(10_000, table.placements().size());
        assertTrue(table.unplaced().isEmpty(), table.unplaced().toString());
        for (LookTable.Placement placement : table.placements().values()) {
            assertNotNull(placement.state());
            if (placement.tier() == LookTable.Tier.STATE) {
                assertEquals(placement.wanted(), placement.drawnAs(), "a borrowed state matches all three of its class");
            }
        }
        // the borrowed states are bounded by the pool, the rest goes to the display layer
        int borrowed = table.count(LookTable.Tier.STATE);
        assertTrue(borrowed > 0 && borrowed <= 930, "borrowed " + borrowed);
        assertEquals(10_000 - borrowed, table.count(LookTable.Tier.DISPLAY));
    }

    @Test
    void aBorrowedStateIsGivenToOneLookOnly() {
        LookTable table = LookAllocator.allocate(POOL, synthetic(10_000), false);
        Set<StateRef> taken = new HashSet<>();
        for (LookTable.Placement placement : table.placements().values()) {
            if (placement.tier() == LookTable.Tier.STATE) {
                assertTrue(taken.add(placement.state()), "borrowed twice: " + placement.state());
            }
        }
        // shells are shared and are never lent out as a borrowed state
        for (LookTable.Placement placement : table.placements().values()) {
            if (placement.tier() == LookTable.Tier.DISPLAY) {
                assertFalse(taken.contains(placement.state()));
            }
        }
    }

    @Test
    void sameContentGivesTheSameTableWhateverTheInputOrder() {
        List<LookRequest> requests = synthetic(3_000);
        LookTable first = LookAllocator.allocate(POOL, requests, false);
        List<LookRequest> shuffled = new ArrayList<>(requests);
        Collections.shuffle(shuffled, new Random(42));
        LookTable second = LookAllocator.allocate(POOL, shuffled, false);
        assertEquals(first.placements(), second.placements());
        assertEquals(new ArrayList<>(first.placements().keySet()), new ArrayList<>(second.placements().keySet()));
        assertEquals(first.warnings(), second.warnings());
    }

    @Test
    void addingALookNeverMovesAnEarlierOneBeforeItInIdOrder() {
        List<LookRequest> requests = synthetic(500);
        LookTable before = LookAllocator.allocate(POOL, requests, false);
        List<LookRequest> more = new ArrayList<>(requests);
        more.add(new LookRequest("zzz:last", DEFAULT, Render.AUTO, false));
        LookTable after = LookAllocator.allocate(POOL, more, false);
        before.placements().forEach((id, placement) -> assertEquals(placement, after.placement(id)));
    }

    @Test
    void firstDefaultLooksTakeNoteBlockStatesInTheOrderOfTheOldAllocator() {
        LookTable table = LookAllocator.allocate(POOL, List.of(request(1, DEFAULT, Render.AUTO),
                request(2, DEFAULT, Render.AUTO)), false);
        assertEquals("minecraft:note_block[instrument=harp,note=0,powered=true]",
                table.placement("test:block_00001").state().blockData());
        assertEquals("minecraft:note_block[instrument=harp,note=1,powered=false]",
                table.placement("test:block_00002").state().blockData());
        assertEquals(1, table.bedrockIndex("test:block_00001").getAsInt());
        assertEquals(2, table.bedrockIndex("test:block_00002").getAsInt());
    }

    @Test
    void displayRenderGoesStraightToAShell() {
        PhysicsClass passable = new PhysicsClass(Collision.NONE, false, SoundGroup.GRASS);
        LookTable table = LookAllocator.allocate(POOL, List.of(request(1, passable, Render.DISPLAY)), false);
        LookTable.Placement placement = table.placement("test:block_00001");
        assertEquals(LookTable.Tier.DISPLAY, placement.tier());
        assertEquals("minecraft:sugar_cane[age=15]", placement.state().blockData());
        assertTrue(placement.exact());
        assertTrue(table.bedrockIndex("test:block_00001").isEmpty());
    }

    @Test
    void theDefaultClassNeedsAShellOfAnotherClassUnlessSolidShellsAreAllowed() {
        List<LookRequest> one = List.of(request(1, DEFAULT, Render.DISPLAY));
        LookTable relaxed = LookAllocator.allocate(POOL, one, false);
        LookTable.Placement placement = relaxed.placement("test:block_00001");
        assertEquals(LookTable.Tier.DISPLAY, placement.tier());
        assertEquals(Collision.FULL, placement.drawnAs().collision());
        assertFalse(placement.exact());
        assertTrue(relaxed.warnings().stream().anyMatch(w -> w.contains("shell of another class")));
        LookTable exact = LookAllocator.allocate(POOL, one, true);
        assertTrue(exact.placement("test:block_00001").exact());
        assertEquals("minecraft:note_block", exact.placement("test:block_00001").state().block());
    }

    @Test
    void aBlockWithoutAnyShellOfItsCollisionIsReportedNotPlaced() {
        ResolvedPool noPassableShells = ResolvedPool.resolve(family -> family.physics().collision() == Collision.NONE
                ? List.of() : LookPool.defaultStates(family));
        LookTable table = LookAllocator.allocate(noPassableShells,
                List.of(request(1, new PhysicsClass(Collision.NONE, false, SoundGroup.GRASS), Render.AUTO)), false);
        assertEquals(List.of("test:block_00001"), table.unplaced());
        assertTrue(table.placements().isEmpty());
    }

    @Test
    void stateRenderThatRunsOutFallsBackToDisplayAndSaysSo() {
        List<LookRequest> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            many.add(request(i, new PhysicsClass(Collision.FULL, true, SoundGroup.STONE), Render.STATE));
        }
        LookTable table = LookAllocator.allocate(POOL, many, false);
        assertEquals(12, table.count(LookTable.Tier.STATE));
        assertEquals(8, table.count(LookTable.Tier.DISPLAY));
        assertTrue(table.warnings().stream().anyMatch(w -> w.contains("prism:render state")));
    }

    @Test
    void bedrockOnlyLooksTakeNoteBlockStatesAndNeverTheDisplayLayer() {
        List<LookRequest> requests = new ArrayList<>();
        for (int i = 0; i < 800; i++) {
            requests.add(new LookRequest("test:model_" + String.format("%04d", i), DEFAULT, Render.AUTO, true));
        }
        LookTable table = LookAllocator.allocate(POOL, requests, false);
        assertEquals(798, table.count(LookTable.Tier.STATE));
        assertEquals(2, table.unplaced().size());
        assertEquals(0, table.count(LookTable.Tier.DISPLAY));
    }

    @Test
    void canonicalisationOnlyAppliesToFamiliesInUse() {
        StateRef note = StateRef.parse("minecraft:note_block[instrument=bass,note=3,powered=true]");
        LookTable none = LookAllocator.allocate(POOL, List.of(), false);
        assertEquals(note, none.canonical(note));
        LookTable used = LookAllocator.allocate(POOL, List.of(request(1, DEFAULT, Render.AUTO)), false);
        assertEquals(StateRef.parse("minecraft:note_block[instrument=harp,note=0,powered=false]"), used.canonical(note));
        assertEquals(Set.of("minecraft:note_block"), used.activeBlocks());
        StateRef target = StateRef.parse("minecraft:target[power=4]");
        assertEquals(target, used.canonical(target));
    }

    @Test
    void breakingComesFromTheFamilyOfTheState() {
        LookTable table = LookAllocator.allocate(POOL, List.of(request(1, DEFAULT, Render.AUTO),
                request(2, new PhysicsClass(Collision.NONE, false, SoundGroup.STONE), Render.AUTO)), false);
        assertEquals(0.8, table.breaking("test:block_00001").hardness());
        assertTrue(table.breaking("test:block_00002").instant());
    }
}
