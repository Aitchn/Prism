package com.aitchn.prism.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aitchn.prism.api.menu.MenuButton;
import com.aitchn.prism.api.menu.MenuSpec;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuSpecTest {

    private static List<MenuButton> buttons(int n) {
        List<MenuButton> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new MenuButton("b" + i, "Button " + i, PrismKey.parse("minecraft:stone")));
        }
        return list;
    }

    @Test
    void rowsRoundUp() {
        assertEquals(1, new MenuSpec("t", buttons(1)).rows());
        assertEquals(1, new MenuSpec("t", buttons(9)).rows());
        assertEquals(2, new MenuSpec("t", buttons(10)).rows());
        assertEquals(6, new MenuSpec("t", buttons(54)).rows());
    }

    @Test
    void rejectsEmptyOversizedAndDuplicateIds() {
        assertThrows(IllegalArgumentException.class, () -> new MenuSpec("t", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MenuSpec("t", buttons(55)));
        MenuButton a = new MenuButton("x", "A", PrismKey.parse("minecraft:stone"));
        assertThrows(IllegalArgumentException.class, () -> new MenuSpec("t", List.of(a, a)));
    }

    @Test
    void artIsOptional() {
        MenuSpec plain = new MenuSpec("t", buttons(3));
        assertEquals(null, plain.art());
        MenuSpec drawn = plain.withArt(PrismKey.parse("m:machine"));
        assertEquals(PrismKey.parse("m:machine"), drawn.art());
        assertEquals(plain.buttons(), drawn.buttons());
    }
}
