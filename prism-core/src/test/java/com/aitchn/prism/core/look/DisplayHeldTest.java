package com.aitchn.prism.core.look;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DisplayHeldTest {

    @Test
    void keysRoundTripEveryChunkLocalPosition() {
        for (int y : new int[] {-2048, -64, 0, 319, 2047}) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int key = DisplayHeld.key(x, y, z);
                    assertTrue(key >= 0);
                    assertEquals(x, DisplayHeld.keyX(key));
                    assertEquals(y, DisplayHeld.keyY(key));
                    assertEquals(z, DisplayHeld.keyZ(key));
                }
            }
        }
    }

    /** Random puts and clears against a plain map: the table never loses or invents an entity. */
    @Test
    void behavesLikeAMap() {
        Random random = new Random(3);
        for (int width : new int[] {1, 7}) {
            DisplayHeld held = new DisplayHeld(width);
            Map<Long, int[]> reference = new HashMap<>();
            int nextId = 1;
            for (int step = 0; step < 50_000; step++) {
                int key = DisplayHeld.key(random.nextInt(16), random.nextInt(40), random.nextInt(16));
                int slot = random.nextInt(width);
                long ref = ((long) key << 3) | slot;
                switch (random.nextInt(4)) {
                    case 0, 1 -> {
                        int id = nextId++;
                        held.set(key, slot, id, slot, id * 31);
                        reference.put(ref, new int[] {id, slot, id * 31});
                    }
                    case 2 -> {
                        int[] old = reference.remove(ref);
                        assertEquals(old == null ? 0 : old[0], held.clear(key, slot));
                    }
                    default -> {
                        int[] removed = held.clearAll(key);
                        int expected = 0;
                        for (int s = 0; s < width; s++) {
                            if (reference.remove(((long) key << 3) | s) != null) {
                                expected++;
                            }
                        }
                        assertEquals(expected, removed.length);
                    }
                }
                if (step % 997 == 0) {
                    check(held, reference);
                }
            }
            check(held, reference);
        }
    }

    private static void check(DisplayHeld held, Map<Long, int[]> reference) {
        assertEquals(reference.size(), held.entities());
        reference.forEach((ref, value) -> {
            int key = (int) (ref >> 3);
            int slot = (int) (ref & 7);
            assertEquals(value[0], held.id(key, slot));
            assertEquals(value[1], held.cellOf(key, slot));
            assertEquals(value[2], held.hashOf(key, slot));
        });
        int[] ids = held.allIds();
        int[] expected = reference.values().stream().mapToInt(v -> v[0]).toArray();
        Arrays.sort(ids);
        Arrays.sort(expected);
        assertEquals(Arrays.toString(expected), Arrays.toString(ids));
        int counted = 0;
        for (int key : held.keys()) {
            counted += held.count(key);
        }
        assertEquals(reference.size(), counted);
    }

    @Test
    void cellsPackTheNeighbourOffset() {
        assertEquals(13, DisplayHeld.cell(0, 0, 0));
        assertEquals(0, DisplayHeld.cell(-1, -1, -1));
        assertEquals(26, DisplayHeld.cell(1, 1, 1));
    }
}
