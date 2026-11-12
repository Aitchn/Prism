package com.aitchn.prism.core.menu;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What the Java pack holds for one {@link MenuArt}: the glyph of the background and the glyphs of each widget's steps
 * or states, with their advances. Made by the pack builder from the textures; read by the server to write titles
 * ({@link MenuTitles}). A widget whose sprite is missing is left out, and shows its fallback item instead.
 *
 * @param background null when the menu has none or its texture is missing or too large
 * @param widgets    by widget id, in drawing order
 */
public record MenuLayout(
        @NotNull MenuArt art,
        @Nullable Glyph background,
        @NotNull Map<String, WidgetGlyphs> widgets) {

    public MenuLayout {
        Objects.requireNonNull(art, "art");
        widgets = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(widgets));
    }

    /**
     * One picture glyph.
     *
     * @param text    the character (one UTF-16 unit) in {@link MenuGlyphs#FONT}
     * @param x       where its left edge goes, from the container's left edge
     * @param advance how far it moves the pen ({@link MenuGlyphs#advance})
     */
    public record Glyph(@NotNull String text, int x, int advance) {}

    /**
     * The glyphs of one widget. Progress and bar: {@code frames.get(n - 1)} is step {@code n} (step 0 draws nothing).
     * Icon: {@code frames.get(n)} is state {@code n}.
     */
    public record WidgetGlyphs(@NotNull MenuArt.Widget widget, @NotNull List<Glyph> frames) {

        public WidgetGlyphs {
            frames = List.copyOf(frames);
        }

        /** The glyph for {@code value} (a step or a state), or null when nothing is drawn. */
        public @Nullable Glyph frame(int value) {
            if (widget.kind() == MenuArt.Kind.ICON) {
                return frames.isEmpty() ? null : frames.get(widget.state(value));
            }
            if (value <= 0) {
                return null;
            }
            return frames.get(Math.min(value, frames.size()) - 1);
        }
    }
}
