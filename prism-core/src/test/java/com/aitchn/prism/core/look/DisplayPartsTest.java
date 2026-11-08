package com.aitchn.prism.core.look;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.content.PrismComponents.Render;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DisplayPartsTest {

    private static final List<String> SIX = List.of("m:f/north", "m:f/east", "m:f/south", "m:f/west", "m:f/up",
            "m:f/down");

    private static DisplayLook look(DisplayPlan plan, int rotX, int rotY) {
        return new DisplayLook(0, "m:b", plan, rotX, rotY, true);
    }

    @Test
    void everyFaceIsDrawnInTheCellItLooksIntoAndMovedBack() {
        List<DisplayLook.Part> parts = DisplayParts.compute(look(DisplayPlan.split(SIX), 0, 0), new boolean[6],
                new int[6]);
        assertEquals(6, parts.size());
        for (DisplayLook.Part part : parts) {
            int[] step = Dir.offset(part.slot());
            assertEquals(step[0], part.dx());
            assertEquals(step[1], part.dy());
            assertEquals(step[2], part.dz());
            assertArrayEquals(new float[] {-step[0], -step[1], -step[2]}, part.translation());
            assertEquals(SIX.get(part.slot()), part.model());
        }
    }

    @Test
    void facesTowardOccludingNeighboursAreSkipped() {
        boolean[] occluded = new boolean[6];
        occluded[Dir.DOWN] = true;
        occluded[Dir.NORTH] = true;
        List<DisplayLook.Part> parts = DisplayParts.compute(look(DisplayPlan.split(SIX), 0, 0), occluded, new int[6]);
        assertEquals(4, parts.size());
        assertTrue(parts.stream().noneMatch(p -> p.slot() == Dir.DOWN || p.slot() == Dir.NORTH));
    }

    @Test
    void aBuriedBlockHasNoEntities() {
        boolean[] all = {true, true, true, true, true, true};
        assertTrue(DisplayParts.compute(look(DisplayPlan.split(SIX), 0, 0), all, new int[6]).isEmpty());
        assertTrue(DisplayParts.compute(look(DisplayPlan.whole("m:b"), 0, 0), all, new int[6]).isEmpty());
    }

    @Test
    void aMissingFaceHasNoEntity() {
        List<String> noDown = List.of("m:f/north", "m:f/east", "m:f/south", "m:f/west", "m:f/up", "");
        assertEquals(5, DisplayParts.compute(look(DisplayPlan.split(noDown), 0, 0), new boolean[6], new int[6]).size());
    }

    @Test
    void aRotatedLookDrawsEachFaceInTheCellItLooksIntoAfterTheTurn() {
        // facing east: y = 90, the model's north face now looks east
        List<DisplayLook.Part> parts = DisplayParts.compute(look(DisplayPlan.split(SIX), 0, 90), new boolean[6],
                new int[6]);
        DisplayLook.Part north = parts.stream().filter(p -> p.slot() == Dir.NORTH).findFirst().orElseThrow();
        assertEquals(1, north.dx());
        assertEquals(0, north.dz());
        boolean[] eastBlocked = new boolean[6];
        eastBlocked[Dir.EAST] = true;
        List<DisplayLook.Part> without = DisplayParts.compute(look(DisplayPlan.split(SIX), 0, 90), eastBlocked,
                new int[6]);
        assertTrue(without.stream().noneMatch(p -> p.slot() == Dir.NORTH), "the north face looks east now");
        assertEquals(5, without.size());
    }

    @Test
    void theQuaternionTurnsFaceNormalsTheSameWayAsTheBlockstateRotation() {
        for (int x : new int[] {0, 90, 180, 270}) {
            for (int y : new int[] {0, 90, 180, 270}) {
                float[] q = Dir.quaternion(x, y);
                for (int dir = 0; dir < 6; dir++) {
                    int[] from = Dir.offset(dir);
                    float[] turned = Dir.apply(q, new float[] {from[0], from[1], from[2]});
                    int[] expected = Dir.offset(Dir.rotate(dir, x, y));
                    assertArrayEquals(new float[] {expected[0], expected[1], expected[2]}, turned, 1e-5f,
                            "x=" + x + " y=" + y + " dir=" + Dir.name(dir));
                }
            }
        }
    }

    @Test
    void blockstateRotationMatchesTheFacingsOfAppearances() {
        assertEquals(Dir.EAST, Dir.rotate(Dir.NORTH, 0, 90));
        assertEquals(Dir.DOWN, Dir.rotate(Dir.NORTH, 90, 0));
        assertEquals(Dir.UP, Dir.rotate(Dir.NORTH, 270, 0));
        assertEquals(Dir.SOUTH, Dir.rotate(Dir.NORTH, 0, 180));
        assertEquals(Dir.WEST, Dir.rotate(Dir.NORTH, 0, 270));
        // up and down do not move with y, east and west do not move with x
        assertEquals(Dir.UP, Dir.rotate(Dir.UP, 0, 90));
        assertEquals(Dir.EAST, Dir.rotate(Dir.EAST, 90, 0));
    }

    @Test
    void aModelThatIsNotSplitIsAnchoredInTheBrightestOpenNeighbour() {
        int[] light = new int[6];
        light[Dir.WEST] = 9;
        light[Dir.EAST] = 12;
        boolean[] occluded = new boolean[6];
        occluded[Dir.UP] = true;
        List<DisplayLook.Part> parts = DisplayParts.compute(look(DisplayPlan.whole("m:whole"), 0, 0), occluded, light);
        assertEquals(1, parts.size());
        assertEquals(DisplayLook.WHOLE, parts.getFirst().slot());
        assertEquals(1, parts.getFirst().dx());
        // ties go to the first of up, north, east, south, west, down that is open
        parts = DisplayParts.compute(look(DisplayPlan.whole("m:whole"), 0, 0), new boolean[6], new int[6]);
        assertEquals(1, parts.getFirst().dy());
    }

    @Test
    void displayTableJoinsPlacementsWithPlansAndReadsTheFacing() {
        PhysicsClass passable = new PhysicsClass(Collision.NONE, false, SoundGroup.GRASS);
        LookTable table = LookAllocator.allocate(ResolvedPool.standard(), List.of(
                new LookRequest("m:a@east", passable, Render.DISPLAY, false),
                new LookRequest("m:b", passable, Render.DISPLAY, false)), false);
        DisplayTable displays = DisplayTable.of(table, Map.of("m:a@east", DisplayPlan.whole("m:a")));
        assertEquals(1, displays.size());
        DisplayLook look = displays.forLook("m:a@east");
        assertEquals(0, look.id());
        assertEquals(90, look.rotY());
        assertEquals(look, displays.get(0));
    }
}
