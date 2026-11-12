package com.aitchn.prism.core.menu;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * The arithmetic of drawing pictures with a font in a container title (ADR-0028). Pure; shared by the pack builder,
 * which makes the glyphs, and the server, which writes the titles.
 *
 * <p>A vanilla container draws its title at (8, 6) from the container's top-left corner, in a font whose line has its
 * baseline 7 pixels below the line's top. A bitmap glyph with ascent {@code a} has its top {@code 7 - a} pixels below
 * the line's top, so a glyph whose top must be at {@code y} pixels below the container's top gets
 * {@code a = 6 + 7 - y = 13 - y}. The client refuses a glyph whose ascent is larger than its height, so such a glyph
 * (a small picture near the top edge) gets transparent rows below it ({@link #padding}).
 *
 * <p>Horizontally, each character moves the pen by its advance. A bitmap glyph advances by its drawn width (up to and
 * including its rightmost column with any alpha) plus one ({@link #advance}). Space characters of the {@code space}
 * provider move the pen by any amount, negative included: twenty-two of them, plus and minus each power of two from 1
 * to 1024, write any shift ({@link #shift}).
 */
public final class MenuGlyphs {

    /** The font that holds every menu glyph and shift: {@code assets/prism/font/menu.json}. */
    public static final String FONT = "prism:menu";
    /** Where vanilla starts the title, from the container's left edge. */
    public static final int TITLE_X = 8;
    /** Where vanilla puts the title line's top, from the container's top edge. */
    public static final int TITLE_Y = 6;
    /** Distance from the top of a text line to its baseline in the default font. */
    public static final int BASELINE = 7;
    /** The first code point of a shift character; shift {@code +2^i} is {@code SHIFT_BASE + i}, {@code -2^i} is 16 more. */
    public static final int SHIFT_BASE = 0xE000;
    /** The powers of two the shifts cover: 2^0 to 2^10, so any shift up to 2047 pixels either way. */
    public static final int SHIFT_BITS = 11;
    /** The first code point given to a picture glyph. */
    public static final int GLYPH_BASE = 0xE100;
    /** The last code point given to a picture glyph (the end of the Basic Multilingual Plane's private use area). */
    public static final int GLYPH_LAST = 0xF8FF;

    private MenuGlyphs() {}

    /** The ascent of a glyph whose top is {@code y} pixels below the container's top edge. */
    public static int ascent(int y) {
        return TITLE_Y + BASELINE - y;
    }

    /**
     * Transparent rows to add below a picture {@code height} pixels high drawn at {@code y}, so that its ascent is not
     * larger than its height.
     */
    public static int padding(int y, int height) {
        return Math.max(0, ascent(y) - height);
    }

    /**
     * How far a bitmap glyph moves the pen: the width up to its rightmost column that has any alpha, plus one; 1 for
     * a glyph with no visible pixel. {@code argb} is row-major, {@code width} wide.
     */
    public static int advance(int @NotNull [] argb, int width, int height) {
        for (int x = width - 1; x >= 0; x--) {
            for (int y = 0; y < height; y++) {
                if ((argb[y * width + x] >>> 24) != 0) {
                    return x + 2;
                }
            }
        }
        return 1;
    }

    /** The characters that move the pen by {@code pixels} (negative moves left); empty for 0. */
    public static @NotNull String shift(int pixels) {
        if (Math.abs(pixels) >= 1 << SHIFT_BITS) {
            throw new IllegalArgumentException("shift out of range: " + pixels);
        }
        StringBuilder out = new StringBuilder();
        int rest = Math.abs(pixels);
        int base = pixels < 0 ? SHIFT_BASE + 16 : SHIFT_BASE;
        for (int bit = SHIFT_BITS - 1; bit >= 0; bit--) {
            if ((rest & (1 << bit)) != 0) {
                out.append((char) (base + bit));
            }
        }
        return out.toString();
    }

    /** The {@code advances} map of the font's space provider: every shift character and its advance. */
    public static @NotNull Map<String, Integer> shiftAdvances() {
        Map<String, Integer> advances = new LinkedHashMap<>();
        for (int bit = 0; bit < SHIFT_BITS; bit++) {
            advances.put(String.valueOf((char) (SHIFT_BASE + bit)), 1 << bit);
        }
        for (int bit = 0; bit < SHIFT_BITS; bit++) {
            advances.put(String.valueOf((char) (SHIFT_BASE + 16 + bit)), -(1 << bit));
        }
        return advances;
    }

    /** The pixels revealed at step {@code step} of {@code steps} over a sprite {@code length} pixels long; at least 1. */
    public static int revealed(int step, int steps, int length) {
        if (step <= 0) {
            return 0;
        }
        if (step >= steps) {
            return length;
        }
        return Math.max(1, Math.min(length, (int) Math.round((double) step * length / steps)));
    }

    /**
     * A copy of {@code argb} ({@code width} by {@code height}) with only the part revealed at {@code step} kept: the
     * fill starts at the side opposite {@code direction} and grows towards it.
     */
    public static int @NotNull [] reveal(int @NotNull [] argb, int width, int height, MenuArt.Direction direction,
            int step, int steps) {
        int[] out = new int[argb.length];
        int shown = revealed(step, steps, direction.horizontal() ? width : height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean keep = switch (direction) {
                    case RIGHT -> x < shown;
                    case LEFT -> x >= width - shown;
                    case DOWN -> y < shown;
                    case UP -> y >= height - shown;
                };
                if (keep) {
                    out[y * width + x] = argb[y * width + x];
                }
            }
        }
        return out;
    }

    /** Whether {@code c} is in a private use area (the menu glyphs and shifts are). */
    public static boolean privateUse(int c) {
        return (c >= 0xE000 && c <= 0xF8FF) || (c >= 0xF0000 && c <= 0x10FFFD);
    }

    /** {@code text} without any private use character. */
    public static @NotNull String clean(@NotNull String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().filter(c -> !privateUse(c)).forEach(out::appendCodePoint);
        return out.toString();
    }

    /** Whether {@code text} has any private use character. */
    public static boolean hasPrivateUse(@NotNull String text) {
        return text.codePoints().anyMatch(MenuGlyphs::privateUse);
    }
}
