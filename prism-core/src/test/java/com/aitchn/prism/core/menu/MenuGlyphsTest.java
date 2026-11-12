package com.aitchn.prism.core.menu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MenuGlyphsTest {

    @Test
    void ascentPutsTheGlyphTopAtTheGivenRow() {
        // The title line's top is at y = 6 and a glyph's top is 7 - ascent below it.
        assertEquals(13, MenuGlyphs.ascent(0)); // the background, at the container's top edge
        assertEquals(-5, MenuGlyphs.ascent(18)); // the first slot row
        for (int y = -20; y < 240; y++) {
            assertEquals(y, MenuGlyphs.TITLE_Y + MenuGlyphs.BASELINE - MenuGlyphs.ascent(y));
        }
    }

    @Test
    void paddingKeepsTheAscentWithinTheHeight() {
        assertEquals(0, MenuGlyphs.padding(0, 71)); // a background is tall enough
        assertEquals(9, MenuGlyphs.padding(0, 4)); // a 4 px sprite at the very top: ascent 13
        assertEquals(0, MenuGlyphs.padding(9, 4));
        assertEquals(0, MenuGlyphs.padding(40, 1));
        for (int y = 0; y < 20; y++) {
            for (int h = 1; h < 20; h++) {
                assertTrue(MenuGlyphs.ascent(y) <= h + MenuGlyphs.padding(y, h));
            }
        }
    }

    @Test
    void advanceIsTheDrawnWidthPlusOne() {
        int[] empty = new int[4 * 2];
        assertEquals(1, MenuGlyphs.advance(empty, 4, 2));
        int[] left = new int[4 * 2];
        left[4] = 0xFF000000; // x 0, y 1
        assertEquals(2, MenuGlyphs.advance(left, 4, 2));
        int[] right = new int[4 * 2];
        right[3] = 0x01FFFFFF; // any alpha counts
        assertEquals(5, MenuGlyphs.advance(right, 4, 2));
        int[] clearColour = new int[4 * 2];
        clearColour[3] = 0x00FFFFFF; // colour without alpha does not
        assertEquals(1, MenuGlyphs.advance(clearColour, 4, 2));
    }

    @Test
    void shiftsAddUpToTheRequestedDistance() {
        Map<String, Integer> advances = MenuGlyphs.shiftAdvances();
        assertEquals(2 * MenuGlyphs.SHIFT_BITS, advances.size());
        assertEquals("", MenuGlyphs.shift(0));
        for (int d = -2047; d <= 2047; d++) {
            int sum = 0;
            for (char c : MenuGlyphs.shift(d).toCharArray()) {
                sum += advances.get(String.valueOf(c));
            }
            assertEquals(d, sum, "shift " + d);
        }
        assertEquals(1, MenuGlyphs.shift(-8).length());
        assertThrows(IllegalArgumentException.class, () -> MenuGlyphs.shift(2048));
    }

    @Test
    void stepsRevealTheSpriteFromTheStartSide() {
        assertEquals(0, MenuGlyphs.revealed(0, 16, 22));
        assertEquals(1, MenuGlyphs.revealed(1, 16, 22));
        assertEquals(11, MenuGlyphs.revealed(8, 16, 22));
        assertEquals(22, MenuGlyphs.revealed(16, 16, 22));
        assertEquals(1, MenuGlyphs.revealed(1, 64, 4)); // more steps than pixels: never empty after step 0
        int[] sprite = {1, 2, 3, 4, 5, 6}; // 3 wide, 2 high
        assertArrayEquals(new int[] {1, 0, 0, 4, 0, 0}, MenuGlyphs.reveal(sprite, 3, 2, MenuArt.Direction.RIGHT, 1, 3));
        assertArrayEquals(new int[] {0, 2, 3, 0, 5, 6}, MenuGlyphs.reveal(sprite, 3, 2, MenuArt.Direction.LEFT, 2, 3));
        assertArrayEquals(new int[] {0, 0, 0, 4, 5, 6}, MenuGlyphs.reveal(sprite, 3, 2, MenuArt.Direction.UP, 1, 2));
        assertArrayEquals(new int[] {1, 2, 3, 0, 0, 0}, MenuGlyphs.reveal(sprite, 3, 2, MenuArt.Direction.DOWN, 1, 2));
        assertArrayEquals(sprite, MenuGlyphs.reveal(sprite, 3, 2, MenuArt.Direction.DOWN, 2, 2));
    }

    @Test
    void privateUseIsFoundAndRemoved() {
        assertTrue(MenuGlyphs.hasPrivateUse("ab"));
        assertTrue(MenuGlyphs.hasPrivateUse(new String(Character.toChars(0xF0001))));
        assertFalse(MenuGlyphs.hasPrivateUse("計數器 Counter"));
        assertEquals("計數器 Counter", MenuGlyphs.clean("計數器 Counter"));
    }
}
