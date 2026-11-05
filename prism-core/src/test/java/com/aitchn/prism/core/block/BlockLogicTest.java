package com.aitchn.prism.core.block;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aitchn.prism.api.content.PrismComponents.Mining;
import com.aitchn.prism.api.content.PrismComponents.Rotation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlockLogicTest {

    @Test
    void facesThePlayer() {
        assertEquals("NORTH", Facings.facing(Rotation.HORIZONTAL, 0, 0)); // player looks south
        assertEquals("EAST", Facings.facing(Rotation.HORIZONTAL, 90, 0)); // looks west
        assertEquals("SOUTH", Facings.facing(Rotation.HORIZONTAL, 180, 0));
        assertEquals("SOUTH", Facings.facing(Rotation.HORIZONTAL, -180, 0));
        assertEquals("WEST", Facings.facing(Rotation.HORIZONTAL, -90, 0)); // looks east
        assertEquals("WEST", Facings.facing(Rotation.HORIZONTAL, 270, 80));
        assertEquals("UP", Facings.facing(Rotation.ALL, 0, 80));
        assertEquals("DOWN", Facings.facing(Rotation.ALL, 0, -80));
        assertEquals("NORTH", Facings.facing(Rotation.NONE, 90, 80));
    }

    @Test
    void breakTimes() {
        Mining stone = new Mining(1.5, "pickaxe");
        assertEquals(30, DigMath.ticksToBreak(stone, "AIR", 0, 0, 0, true, false));
        assertEquals(4, DigMath.ticksToBreak(stone, "DIAMOND_PICKAXE", 0, 0, 0, true, false));
        assertEquals(30, DigMath.ticksToBreak(stone, "DIAMOND_AXE", 0, 0, 0, true, false));
        assertEquals(150, DigMath.ticksToBreak(stone, "AIR", 0, 0, 0, false, false));
        assertEquals(0, DigMath.ticksToBreak(new Mining(0, "none"), "AIR", 0, 0, 0, true, false));
        assertEquals(DigMath.UNBREAKABLE, DigMath.ticksToBreak(new Mining(-1, "none"), "AIR", 0, 0, 0, true, false));
        assertEquals("axe", DigMath.toolType("NETHERITE_AXE"));
        assertEquals("pickaxe", DigMath.toolType("WOODEN_PICKAXE"));
        assertEquals(9, DigMath.stage(0.99));
    }

    @Test
    void positions() {
        assertEquals("b/3/-12/15", BlockPositions.key(3, -12, 15));
        assertArrayEquals(new int[] {3, -12, 15}, BlockPositions.parse("b/3/-12/15"));
        assertNull(BlockPositions.parse("b/16/0/0"));
        assertNull(BlockPositions.parse("x"));
        assertNotEquals(BlockPositions.pack(0, -1, 0), BlockPositions.pack(0, 1, 0));
        assertNotEquals(BlockPositions.pack(15, 0, 0), BlockPositions.pack(0, 1, 0));
        assertNotEquals(BlockPositions.chunkKey(-1, 0), BlockPositions.chunkKey(0, -1));
    }

    @Test
    void ticksByInterval() {
        TickSchedule<String> schedule = new TickSchedule<>();
        schedule.add(1, "every", 1);
        schedule.add(2, "third", 3);
        List<String> ran = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            schedule.tick(ran::add);
        }
        assertEquals(6, ran.stream().filter("every"::equals).count());
        assertEquals(2, ran.stream().filter("third"::equals).count());
        schedule.remove(1);
        schedule.remove(2);
        assertEquals(true, schedule.isEmpty());
    }
}
