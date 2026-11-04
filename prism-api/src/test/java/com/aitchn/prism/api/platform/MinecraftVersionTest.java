package com.aitchn.prism.api.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MinecraftVersionTest {

    @Test
    void parsesTwoAndThreeComponents() {
        assertEquals(new MinecraftVersion(26, 3, 0), MinecraftVersion.parse("26.3"));
        assertEquals(new MinecraftVersion(1, 21, 4), MinecraftVersion.parse("1.21.4"));
    }

    @Test
    void ignoresSuffix() {
        assertEquals(new MinecraftVersion(1, 21, 5), MinecraftVersion.parse("1.21.5-pre1"));
        assertEquals(new MinecraftVersion(26, 3, 0), MinecraftVersion.parse(" 26.3 (Paper) "));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse("snapshot"));
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse("26"));
    }

    @Test
    void ordersAcrossVersioningSchemes() {
        assertTrue(MinecraftVersion.parse("26.3").isAtLeast(MinecraftVersion.parse("1.21.11")));
        assertTrue(MinecraftVersion.parse("1.21.4").isAtLeast(MinecraftVersion.parse("1.21.4")));
        assertTrue(MinecraftVersion.parse("1.21.3").compareTo(MinecraftVersion.parse("1.21.4")) < 0);
    }

    @Test
    void toStringOmitsZeroPatch() {
        assertEquals("26.3", MinecraftVersion.parse("26.3.0").toString());
        assertEquals("1.21.4", MinecraftVersion.parse("1.21.4").toString());
    }
}
