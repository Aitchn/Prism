package com.aitchn.prism.core.menu;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Writes the titles that draw a menu's art (ADR-0028). A Java title is a run of {@link MenuGlyphs#FONT} characters
 * (the background and the widgets, each placed with shifts) followed by the translated title text in the default
 * font; a Bedrock title is the translated text alone, with no private use character.
 */
public final class MenuTitles {

    private MenuTitles() {}

    /** Every widget at its starting value: progress and bar empty, icons in state 0. */
    public static @NotNull Map<String, Integer> initial(@NotNull MenuArt art) {
        Map<String, Integer> values = new LinkedHashMap<>();
        art.widgets().forEach(widget -> values.put(widget.id(), 0));
        return values;
    }

    /**
     * The font run of a Java title: the pen starts where vanilla starts the title ({@link MenuGlyphs#TITLE_X}), moves
     * to each picture's x, draws it, and finally moves to the art's title x. When the title is hidden the last move
     * is left out. {@code values} holds each widget's step or state; a missing value is 0.
     */
    public static @NotNull String glyphs(@NotNull MenuLayout layout, @NotNull Map<String, Integer> values) {
        StringBuilder out = new StringBuilder();
        int pen = MenuGlyphs.TITLE_X;
        if (layout.background() != null) {
            pen = draw(out, pen, layout.background());
        }
        for (MenuLayout.WidgetGlyphs widget : layout.widgets().values()) {
            MenuLayout.Glyph glyph = widget.frame(values.getOrDefault(widget.widget().id(), 0));
            if (glyph != null) {
                pen = draw(out, pen, glyph);
            }
        }
        if (layout.art().titleShown()) {
            out.append(MenuGlyphs.shift(layout.art().titleX() - pen));
        }
        return out.toString();
    }

    private static int draw(StringBuilder out, int pen, MenuLayout.Glyph glyph) {
        out.append(MenuGlyphs.shift(glyph.x() - pen)).append(glyph.text());
        return glyph.x() + glyph.advance();
    }

    /** The whole Java title as plain text: the font run, then {@code text} unless the art hides the title. */
    public static @NotNull String java(@NotNull MenuLayout layout, @NotNull Map<String, Integer> values,
            @NotNull String text) {
        return glyphs(layout, values) + (layout.art().titleShown() ? text : "");
    }

    /** The Bedrock title: {@code text} without any private use character, whatever the art. */
    public static @NotNull String bedrock(@NotNull String text) {
        return MenuGlyphs.clean(text);
    }

    /** Where the pen ends after {@link #glyphs}, for tests: the art's title x, or after the last picture. */
    public static int penAfter(@NotNull MenuLayout layout, @NotNull Map<String, Integer> values) {
        int pen = MenuGlyphs.TITLE_X;
        String run = glyphs(layout, values);
        Map<Character, Integer> advances = new java.util.HashMap<>();
        MenuGlyphs.shiftAdvances().forEach((c, advance) -> advances.put(c.charAt(0), advance));
        if (layout.background() != null) {
            advances.put(layout.background().text().charAt(0), layout.background().advance());
        }
        for (MenuLayout.WidgetGlyphs widget : layout.widgets().values()) {
            widget.frames().forEach(glyph -> advances.put(glyph.text().charAt(0), glyph.advance()));
        }
        for (char c : run.toCharArray()) {
            pen += advances.getOrDefault(c, 0);
        }
        return pen;
    }
}
