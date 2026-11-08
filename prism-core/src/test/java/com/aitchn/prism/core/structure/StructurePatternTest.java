package com.aitchn.prism.core.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.content.PrismComponents;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StructurePatternTest {

    @Test
    void cellsAreOffsetsFromTheController() {
        var pattern = StructurePattern.compile(new PrismComponents.Structure(List.of("RRR/RRR/RRR", "   / @ /   "),
                Map.of('R', "m:ruby"), false));
        assertEquals(1, pattern.rotations());
        assertEquals(9, pattern.cells(0).size());
        assertTrue(pattern.cells(0).contains(new StructurePattern.Cell(-1, -1, -1, "m:ruby")));
        assertTrue(pattern.cells(0).contains(new StructurePattern.Cell(0, -1, 0, "m:ruby")));
        assertEquals(1, pattern.reach());
    }

    @Test
    void rotationsTurnClockwiseAndMatchFindsTheFacing() {
        // A controller with one block in front of it (north) and air behind.
        var pattern = StructurePattern.compile(new PrismComponents.Structure(List.of("R/@/."), Map.of('R', "m:ruby"),
                true));
        assertEquals(4, pattern.rotations());
        assertTrue(pattern.cells(0).contains(new StructurePattern.Cell(0, 0, -1, "m:ruby")));
        assertTrue(pattern.cells(1).contains(new StructurePattern.Cell(1, 0, 0, "m:ruby")));
        assertTrue(pattern.cells(2).contains(new StructurePattern.Cell(0, 0, 1, "m:ruby")));
        assertTrue(pattern.cells(3).contains(new StructurePattern.Cell(-1, 0, 0, "m:ruby")));
        // The world: ruby to the west, air to the east.
        Set<List<Integer>> ruby = Set.of(List.of(-1, 0, 0));
        int turn = pattern.match(cell -> cell.expect().equals("air")
                ? !ruby.contains(List.of(cell.dx(), cell.dy(), cell.dz()))
                : ruby.contains(List.of(cell.dx(), cell.dy(), cell.dz())));
        assertEquals(3, turn);
        assertEquals("WEST", StructurePattern.FACINGS.get(turn));
    }
}
