package com.aitchn.prism.core.menu;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The art of a menu (ADR-0028): a background picture drawn behind the container's slots, pixel widgets drawn over it,
 * and the slots the picture uses. Declared in {@code <content root>/menus/<path>.yml}; a menu refers to it by its key
 * ({@code ContainerMenu#art}, {@code MenuSpec#art}). The Java resource pack turns it into font glyphs; Bedrock players
 * get the plain chest and the widgets' fallback icons.
 *
 * @param background a texture key ({@code ns:gui/machine} is {@code assets/ns/textures/gui/machine.png}), or null for
 *                   none (widgets over the vanilla chest)
 * @param rows       container rows, 1 to 6; the menu's own row count is replaced by this one
 * @param titleX     where the title text starts, in pixels from the container's left edge (vanilla: 8)
 * @param titleShown false hides the title on Java (Bedrock always shows it)
 * @param slots      the slots the picture draws; every other slot is hidden (inert and empty). Empty: all are used
 * @param widgets    in drawing order
 */
public record MenuArt(
        @NotNull PrismKey key,
        @Nullable PrismKey background,
        int rows,
        int titleX,
        boolean titleShown,
        @NotNull Set<Integer> slots,
        @NotNull List<Widget> widgets) {

    /** Steps of a progress or bar widget when the file gives none. */
    public static final int DEFAULT_STEPS = 16;
    /** Most steps a progress or bar widget may have. */
    public static final int MAX_STEPS = 64;
    /** Most states an icon widget may have. */
    public static final int MAX_STATES = 16;
    /** Most widgets one menu may have. */
    public static final int MAX_WIDGETS = 32;
    /** The client's limit for the height of one bitmap glyph; widths are held to the same limit. */
    public static final int MAX_GLYPH_SIZE = 256;

    private static final Set<String> KEYS = Set.of("background", "rows", "title", "slots", "widgets");
    private static final Set<String> WIDGET_KEYS = Set.of("type", "x", "y", "direction", "steps", "sprite", "sprites",
            "color", "width", "height", "fallback");

    public MenuArt {
        Objects.requireNonNull(key, "key");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1 to 6, got " + rows);
        }
        slots = Set.copyOf(slots);
        widgets = List.copyOf(widgets);
    }

    /** Number of container slots. */
    public int size() {
        return rows * 9;
    }

    /** Whether {@code slot} is outside the picture: inert and kept empty for every viewer. */
    public boolean hidden(int slot) {
        return !slots.isEmpty() && !slots.contains(slot);
    }

    /** The widget called {@code id}, or null. */
    public @Nullable Widget widget(@NotNull String id) {
        for (Widget widget : widgets) {
            if (widget.id().equals(id)) {
                return widget;
            }
        }
        return null;
    }

    /** What a widget shows. */
    public enum Kind {
        /** A sprite revealed in steps, such as an arrow that fills. */
        PROGRESS,
        /** A fill in steps, such as an energy bar or a tank, from a sprite or a generated colour. */
        BAR,
        /** One of several sprites, such as on and off. */
        ICON
    }

    /** The side a progress or bar fill grows towards. */
    public enum Direction {
        RIGHT, LEFT, UP, DOWN;

        /** Whether the fill runs along x. */
        public boolean horizontal() {
            return this == RIGHT || this == LEFT;
        }
    }

    /**
     * One pixel widget.
     *
     * @param x             left edge, in pixels from the container's left edge
     * @param y             top edge, in pixels from the container's top edge
     * @param steps         progress and bar: the number of non-empty steps (1 to {@link #MAX_STEPS}); icon: the
     *                      number of states
     * @param sprites       progress and bar: zero or one sprite (zero: generated from {@code color}); icon: one per
     *                      state
     * @param color         {@code 0xRRGGBB} of a generated bar, or -1
     * @param width         size of a generated bar (0 when drawn from a sprite)
     * @param height        size of a generated bar (0 when drawn from a sprite)
     * @param fallbackSlot  the slot that shows the value as an item where the picture is not drawn (Bedrock, no
     *                      pack), or -1 for none
     * @param fallbackItems progress and bar: one gauge item ({@code prism:gauge}); icon: one item per state
     */
    public record Widget(
            @NotNull String id,
            @NotNull Kind kind,
            int x,
            int y,
            @NotNull Direction direction,
            int steps,
            @NotNull List<PrismKey> sprites,
            int color,
            int width,
            int height,
            int fallbackSlot,
            @NotNull List<PrismKey> fallbackItems) {

        public Widget {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(direction, "direction");
            sprites = List.copyOf(sprites);
            fallbackItems = List.copyOf(fallbackItems);
        }

        /**
         * The step that shows {@code fraction} (0 empty, 1 full): 0 only when empty and {@link #steps} only when
         * full, as for gauges ({@code prism:gauge}), so a nearly empty or nearly full value still differs from the
         * ends.
         */
        public int step(double fraction) {
            if (!(fraction > 0)) {
                return 0;
            }
            if (fraction >= 1) {
                return steps;
            }
            int step = (int) Math.round(fraction * steps);
            return steps < 2 ? step : Math.max(1, Math.min(steps - 1, step));
        }

        /** {@code state} clamped to the icon's states. */
        public int state(int state) {
            return Math.max(0, Math.min(steps - 1, state));
        }
    }

    /** Reads a menu file. */
    public static @NotNull MenuArt decode(@NotNull PrismKey key, @NotNull ConfigSection section)
            throws ContentException {
        section.rejectUnknownKeys(KEYS);
        PrismKey background = section.has("background") ? key(section, "background", key.namespace()) : null;
        int rows = section.getInt("rows").orElse(3);
        if (rows < 1 || rows > 6) {
            throw new ContentException(child(section, "rows"), "rows must be 1 to 6, got " + rows);
        }
        int titleX = 8;
        boolean titleShown = true;
        if (section.isSection("title")) {
            ConfigSection title = section.requireSection("title");
            title.rejectUnknownKeys(Set.of("x", "shown"));
            titleX = title.getInt("x").orElse(8);
            titleShown = title.getBoolean("shown", true);
        } else if (section.has("title")) {
            titleShown = section.getBoolean("title", true);
        }
        if (titleX < -MAX_GLYPH_SIZE || titleX > MAX_GLYPH_SIZE) {
            throw new ContentException(child(section, "title.x"), "title x must be within ±" + MAX_GLYPH_SIZE);
        }
        Set<Integer> slots = slots(section, rows * 9);
        List<Widget> widgets = new ArrayList<>();
        Set<Integer> fallbackSlots = new TreeSet<>();
        if (section.has("widgets")) {
            ConfigSection all = section.requireSection("widgets");
            if (all.keys().size() > MAX_WIDGETS) {
                throw new ContentException(child(section, "widgets"), "at most " + MAX_WIDGETS + " widgets");
            }
            for (String id : all.keys()) {
                Widget widget = widget(id, all.requireSection(id), key.namespace(), rows * 9);
                if (widget.fallbackSlot() >= 0 && !fallbackSlots.add(widget.fallbackSlot())) {
                    throw new ContentException(child(all, id + ".fallback.slot"),
                            "slot " + widget.fallbackSlot() + " is the fallback of two widgets");
                }
                widgets.add(widget);
            }
        }
        if (background == null && widgets.isEmpty()) {
            throw new ContentException(section.path(), "a menu file needs a background, widgets or both");
        }
        return new MenuArt(key, background, rows, titleX, titleShown, slots, widgets);
    }

    private static Widget widget(String id, ConfigSection section, String namespace, int size)
            throws ContentException {
        section.rejectUnknownKeys(WIDGET_KEYS);
        String typeName = section.requireString("type").toUpperCase(Locale.ROOT);
        Kind kind;
        try {
            kind = Kind.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            throw new ContentException(child(section, "type"), "type must be progress, bar or icon");
        }
        int x = section.requireInt("x");
        int y = section.requireInt("y");
        if (Math.abs(x) > MAX_GLYPH_SIZE || Math.abs(y) > MAX_GLYPH_SIZE) {
            throw new ContentException(section.path(), "x and y must be within ±" + MAX_GLYPH_SIZE);
        }
        Direction direction = kind == Kind.BAR ? Direction.UP : Direction.RIGHT;
        if (section.has("direction")) {
            try {
                direction = Direction.valueOf(section.requireString("direction").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ContentException(child(section, "direction"), "direction must be right, left, up or down");
            }
        }
        List<PrismKey> sprites = new ArrayList<>();
        int color = -1;
        int width = 0;
        int height = 0;
        int steps;
        if (kind == Kind.ICON) {
            for (String sprite : section.getStringList("sprites")) {
                sprites.add(parse(child(section, "sprites"), sprite, namespace));
            }
            if (sprites.isEmpty() || sprites.size() > MAX_STATES) {
                throw new ContentException(child(section, "sprites"),
                        "an icon widget needs 1 to " + MAX_STATES + " sprites, one per state");
            }
            steps = sprites.size();
        } else {
            steps = section.getInt("steps").orElse(DEFAULT_STEPS);
            if (steps < 1 || steps > MAX_STEPS) {
                throw new ContentException(child(section, "steps"), "steps must be 1 to " + MAX_STEPS);
            }
            if (section.has("sprite")) {
                sprites.add(key(section, "sprite", namespace));
            } else if (kind == Kind.BAR && section.has("color")) {
                color = color(section);
                width = section.requireInt("width");
                height = section.requireInt("height");
                if (width < 1 || height < 1 || width > MAX_GLYPH_SIZE || height > MAX_GLYPH_SIZE) {
                    throw new ContentException(section.path(), "width and height must be 1 to " + MAX_GLYPH_SIZE);
                }
            } else {
                throw new ContentException(section.path(), kind == Kind.BAR
                        ? "a bar widget needs a sprite, or a color with a width and a height"
                        : "a progress widget needs a sprite");
            }
        }
        int fallbackSlot = -1;
        List<PrismKey> fallbackItems = new ArrayList<>();
        if (section.has("fallback")) {
            ConfigSection fallback = section.requireSection("fallback");
            fallback.rejectUnknownKeys(Set.of("slot", "item", "items"));
            fallbackSlot = fallback.requireInt("slot");
            if (fallbackSlot < 0 || fallbackSlot >= size) {
                throw new ContentException(child(fallback, "slot"), "slot must be 0 to " + (size - 1));
            }
            if (kind == Kind.ICON) {
                for (String item : fallback.getStringList("items")) {
                    fallbackItems.add(parse(child(fallback, "items"), item, namespace));
                }
                if (fallbackItems.size() != steps) {
                    throw new ContentException(child(fallback, "items"), "an icon widget's fallback needs one item"
                            + " per state (" + steps + ")");
                }
            } else {
                fallbackItems.add(key(fallback, "item", namespace));
            }
        }
        return new Widget(id, kind, x, y, direction, steps, sprites, color, width, height, fallbackSlot,
                fallbackItems);
    }

    /** {@code slots}: a list of slot numbers and ranges such as {@code 10-16}. */
    private static Set<Integer> slots(ConfigSection section, int size) throws ContentException {
        Set<Integer> slots = new TreeSet<>();
        for (String entry : section.getStringList("slots")) {
            String text = entry.trim();
            try {
                int dash = text.indexOf('-', 1);
                int from = Integer.parseInt(dash < 0 ? text : text.substring(0, dash).trim());
                int to = dash < 0 ? from : Integer.parseInt(text.substring(dash + 1).trim());
                if (from < 0 || to >= size || from > to) {
                    throw new ContentException(child(section, "slots"), "slot " + text + " is not within 0 to "
                            + (size - 1));
                }
                for (int slot = from; slot <= to; slot++) {
                    slots.add(slot);
                }
            } catch (NumberFormatException e) {
                throw new ContentException(child(section, "slots"), "not a slot or a range: " + text);
            }
        }
        return slots;
    }

    private static int color(ConfigSection section) throws ContentException {
        String text = section.requireString("color");
        if (!text.matches("#[0-9a-fA-F]{6}")) {
            throw new ContentException(child(section, "color"), "color must be #rrggbb");
        }
        return Integer.parseInt(text.substring(1), 16);
    }

    private static PrismKey key(ConfigSection section, String field, String namespace) throws ContentException {
        return parse(child(section, field), section.requireString(field), namespace);
    }

    private static PrismKey parse(String path, String text, String namespace) throws ContentException {
        try {
            return PrismKey.parse(text, namespace);
        } catch (IllegalArgumentException e) {
            throw new ContentException(path, e.getMessage());
        }
    }

    private static String child(ConfigSection section, String key) {
        return section.path().isEmpty() ? key : section.path() + "." + key;
    }

    /** Menus by key, in key order (the pack and the self test read them in this order). */
    public static @NotNull Map<PrismKey, MenuArt> sorted(@NotNull Iterable<MenuArt> menus) {
        Map<String, MenuArt> byText = new java.util.TreeMap<>();
        menus.forEach(menu -> byText.put(menu.key().toString(), menu));
        Map<PrismKey, MenuArt> result = new LinkedHashMap<>();
        byText.values().forEach(menu -> result.put(menu.key(), menu));
        return result;
    }
}
