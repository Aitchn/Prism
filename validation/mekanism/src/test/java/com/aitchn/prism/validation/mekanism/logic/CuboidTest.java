package com.aitchn.prism.validation.mekanism.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CuboidTest {

    private static Set<Pos> box(int size) {
        Set<Pos> casing = new HashSet<>();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    if (x == 0 || y == 0 || z == 0 || x == size - 1 || y == size - 1 || z == size - 1) {
                        casing.add(new Pos("w", x, y, z));
                    }
                }
            }
        }
        return casing;
    }

    @Test
    void completeBoxIsValidWithInteriorVolume() {
        Set<Pos> casing = box(5);
        var result = Cuboid.validate(new Pos("w", 0, 0, 0), casing::contains, p -> true, 3, 18);
        assertTrue(result.valid(), result.problem());
        assertEquals(27, result.interiorVolume());
    }

    @Test
    void holeInTheWallIsReported() {
        Set<Pos> casing = box(4);
        casing.remove(new Pos("w", 1, 0, 1));
        var result = Cuboid.validate(new Pos("w", 0, 0, 0), casing::contains, p -> true, 3, 18);
        assertFalse(result.valid());
        assertTrue(result.problem().contains("hole"));
    }

    @Test
    void tooSmallAndBlockedInteriorFail() {
        assertFalse(Cuboid.validate(new Pos("w", 0, 0, 0), box(2)::contains, p -> true, 3, 18).valid());
        Set<Pos> casing = box(5);
        var blocked = Cuboid.validate(new Pos("w", 0, 0, 0), casing::contains, p -> p.x() != 2, 3, 18);
        assertFalse(blocked.valid());
    }
}
