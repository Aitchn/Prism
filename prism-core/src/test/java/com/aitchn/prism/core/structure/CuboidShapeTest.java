package com.aitchn.prism.core.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.content.PrismComponents;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CuboidShapeTest {

    private static final String WALL = "m:wall";
    private static final String GLASS = "minecraft:glass";
    private static final String CORE = "m:core";
    private static final CuboidShape SHAPE = new CuboidShape(
            new PrismComponents.Cuboid(List.of(WALL, GLASS), List.of("m:pipe"), 3, 6));

    /** A fake world: cells listed are blocks, everything else air; cells in {@code unloaded} cannot be seen. */
    private static final class World {
        final Map<List<Integer>, String> blocks = new HashMap<>();
        final java.util.Set<List<Integer>> unloaded = new java.util.HashSet<>();
        int cx;
        int cy;
        int cz;

        /** A box of walls with corners (x0,y0,z0) and (x1,y1,z1), hollow. */
        World box(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        if (x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1) {
                            blocks.put(List.of(x, y, z), WALL);
                        }
                    }
                }
            }
            return this;
        }

        World controller(int x, int y, int z) {
            blocks.put(List.of(x, y, z), CORE);
            cx = x;
            cy = y;
            cz = z;
            return this;
        }

        Boolean is(int dx, int dy, int dz, String expect) {
            List<Integer> at = List.of(cx + dx, cy + dy, cz + dz);
            if (unloaded.contains(at)) {
                return null;
            }
            String block = blocks.get(at);
            return expect.equals("air") ? block == null : expect.equals(block);
        }

        StructureShape.Result find() {
            return SHAPE.find(this::is);
        }
    }

    @Test
    void findsBoxesOfEverySizeInRangeFromAnyFace() {
        var world = new World().box(0, 0, 0, 4, 3, 5).controller(2, 3, 2); // controller in the roof
        var match = world.find().match();
        assertNotNull(match);
        assertEquals("DOWN", match.facing());
        assertEquals(5 * 4 * 6 - 3 * 2 * 4 - 1, match.members().size());
        assertEquals(5 * 4 * 6 - 1, match.watched().size());

        var side = new World().box(0, 0, 0, 2, 2, 2).controller(0, 1, 1).find().match();
        assertNotNull(side);
        assertEquals("EAST", side.facing());
        assertEquals("EAST 0,-1,-1 2,1,1", side.signature());
    }

    @Test
    void rejectsWrongWallsBlockedInsidesAndSizes() {
        var world = new World().box(0, 0, 0, 4, 4, 4).controller(2, 4, 2);
        world.blocks.put(List.of(0, 0, 0), "minecraft:dirt"); // a corner of the wrong block
        assertNull(world.find().match());

        world = new World().box(0, 0, 0, 4, 4, 4).controller(2, 4, 2);
        world.blocks.put(List.of(2, 2, 2), "minecraft:dirt"); // something inside
        assertNull(world.find().match());

        world = new World().box(0, 0, 0, 4, 4, 4).controller(2, 4, 2);
        world.blocks.put(List.of(2, 2, 2), "m:pipe"); // an allowed inside block is a member
        var match = world.find().match();
        assertNotNull(match);
        assertTrue(match.members().contains(new StructureShape.Offset(0, -2, 0)));

        world = new World().box(0, 0, 0, 6, 4, 4).controller(3, 4, 2); // 7 long, max is 6
        assertNull(world.find().match());

        world = new World().box(0, 0, 0, 4, 4, 4).controller(0, 0, 0); // a corner is not a face
        assertNull(world.find().match());
    }

    @Test
    void mixedWallBlocksCount() {
        var world = new World().box(0, 0, 0, 3, 3, 3).controller(1, 3, 1);
        world.blocks.put(List.of(0, 1, 1), GLASS);
        assertNotNull(world.find().match());
    }

    @Test
    void anUnloadedCellMakesTheAnswerUncertain() {
        var world = new World().box(0, 0, 0, 4, 4, 4).controller(2, 4, 2);
        world.unloaded.add(List.of(4, 0, 4));
        var result = world.find();
        assertNull(result.match());
        assertTrue(result.uncertain());

        var open = new World().controller(0, 0, 0).find();
        assertNull(open.match());
        assertFalse(open.uncertain());
    }

    @Test
    void verifyLooksOnlyAtTheEarlierBox() {
        var world = new World().box(0, 0, 0, 2, 2, 2).controller(0, 1, 1); // west face
        String signature = world.find().match().signature();
        for (int y = -3; y <= 5; y++) {
            for (int z = -3; z <= 5; z++) {
                world.unloaded.add(List.of(-3, y, z)); // a column of unloaded chunk west of the box
            }
        }
        world.blocks.remove(List.of(2, 2, 2)); // break the far corner
        var found = world.find();
        assertNull(found.match());
        assertTrue(found.uncertain()); // the west side walks into the unloaded chunk
        assertFalse(SHAPE.verify(world::is, signature).uncertain()); // but the box itself is visibly broken

        world.blocks.put(List.of(2, 2, 2), WALL);
        world.unloaded.add(List.of(2, 2, 2));
        assertTrue(SHAPE.verify(world::is, signature).uncertain());
        assertNull(SHAPE.verify(world::is, "nonsense").match());
    }

    @Test
    void patternsAreShapesToo() {
        var pattern = StructurePattern.compile(new PrismComponents.Structure(List.of("RRR", " @ "),
                Map.of('R', WALL), false));
        var result = pattern.find((dx, dy, dz, expect) -> dy == -1 ? expect.equals(WALL) : null);
        assertNotNull(result.match());
        assertEquals("NORTH", result.match().signature());
        assertEquals(3, result.match().members().size());
        var missing = pattern.find((dx, dy, dz, expect) -> dx == 1 ? null : expect.equals(WALL));
        assertNull(missing.match());
        assertTrue(missing.uncertain());
        assertTrue(pattern.verify((dx, dy, dz, expect) -> dx == 1 ? null : expect.equals(WALL), "NORTH").uncertain());
        assertFalse(pattern.verify((dx, dy, dz, expect) -> dx == 1 ? null : dx == 0 ? false : expect.equals(WALL),
                "NORTH").uncertain());
        assertEquals(5, SHAPE.reach());
    }
}
