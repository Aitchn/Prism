package com.aitchn.prism.pack;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.core.menu.MenuArt;
import com.aitchn.prism.core.menu.MenuGlyphs;
import com.aitchn.prism.core.menu.MenuLayout;
import com.aitchn.prism.pack.json.JsonWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Turns menu art (ADR-0028) into the font {@code prism:menu}: one bitmap glyph per background, per widget step and per
 * icon state, plus the shift characters. Every glyph texture is re-encoded as RGBA (so the advance the server computes
 * is the one the client computes), padded below when its ascent needs it, and stored once by content under
 * {@code assets/prism/textures/font/menu/<hash>.png}. Glyphs are shared: the same picture at the same height on the
 * screen is one character, whichever menus use it. Characters are given in menu key order, so the same content always
 * makes the same font.
 */
public final class MenuArtPack {

    /** The font file. */
    public static final String FONT_PATH = "assets/prism/font/menu.json";
    /** Where the glyph textures go. */
    public static final String TEXTURE_DIR = "assets/prism/textures/font/menu/";

    /**
     * @param layouts     by menu key, for every menu that has at least one picture in the pack
     * @param backgrounds menus whose background is in the pack
     * @param widgets     widgets drawn by the pack
     * @param steps       distinct glyphs used by widget steps and states
     * @param glyphs      distinct glyphs in the font
     */
    public record Result(@NotNull Map<PrismKey, MenuLayout> layouts, int backgrounds, int widgets, int steps,
            int glyphs) {

        public static final Result EMPTY = new Result(Map.of(), 0, 0, 0, 0);

        public Result {
            layouts = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(layouts));
        }
    }

    private record Image(int[] argb, int width, int height) {}

    private record Provider(String file, int ascent, int height, String chars) {}

    private final Map<String, byte[]> files;
    private final List<String> warnings;
    private final Map<String, String> chars = new LinkedHashMap<>();
    private final List<Provider> providers = new ArrayList<>();
    private int next = MenuGlyphs.GLYPH_BASE;

    private MenuArtPack(Map<String, byte[]> files, List<String> warnings) {
        this.files = files;
        this.warnings = warnings;
    }

    /** Adds the font and its textures to {@code files} for {@code menus}; problems go to {@code warnings}. */
    public static @NotNull Result add(@NotNull Map<String, byte[]> files, @NotNull List<String> warnings,
            @NotNull Collection<MenuArt> menus) {
        if (menus.isEmpty()) {
            return Result.EMPTY;
        }
        return new MenuArtPack(files, warnings).build(MenuArt.sorted(menus).values());
    }

    private Result build(Collection<MenuArt> menus) {
        Map<PrismKey, MenuLayout> layouts = new LinkedHashMap<>();
        Set<String> stepChars = new LinkedHashSet<>();
        int backgrounds = 0;
        int widgets = 0;
        for (MenuArt art : menus) {
            String label = "menu " + art.key();
            MenuLayout.Glyph background = null;
            if (art.background() != null) {
                Image image = texture(label + " background", art.background());
                background = image == null ? null : glyph(label + " background", image, 0, 0);
                if (background != null) {
                    backgrounds++;
                }
            }
            Map<String, MenuLayout.WidgetGlyphs> drawn = new LinkedHashMap<>();
            for (MenuArt.Widget widget : art.widgets()) {
                List<MenuLayout.Glyph> frames = frames(label + " widget " + widget.id(), widget);
                if (frames != null) {
                    drawn.put(widget.id(), new MenuLayout.WidgetGlyphs(widget, frames));
                    frames.forEach(frame -> stepChars.add(frame.text()));
                    widgets++;
                }
            }
            if (background != null || !drawn.isEmpty()) {
                layouts.put(art.key(), new MenuLayout(art, background, drawn));
            }
        }
        if (!providers.isEmpty()) {
            files.put(FONT_PATH, font().getBytes(StandardCharsets.UTF_8));
        }
        return new Result(layouts, backgrounds, widgets, stepChars.size(), chars.size());
    }

    /** The glyphs of every step (progress, bar) or state (icon); null when a sprite is missing or too large. */
    private @Nullable List<MenuLayout.Glyph> frames(String label, MenuArt.Widget widget) {
        List<MenuLayout.Glyph> frames = new ArrayList<>();
        if (widget.kind() == MenuArt.Kind.ICON) {
            for (PrismKey sprite : widget.sprites()) {
                Image image = texture(label, sprite);
                MenuLayout.Glyph glyph = image == null ? null : glyph(label, image, widget.x(), widget.y());
                if (glyph == null) {
                    return null;
                }
                frames.add(glyph);
            }
            return frames;
        }
        Image full = widget.sprites().isEmpty() ? generated(widget) : texture(label, widget.sprites().getFirst());
        if (full == null) {
            return null;
        }
        for (int step = 1; step <= widget.steps(); step++) {
            int[] shown = MenuGlyphs.reveal(full.argb(), full.width(), full.height(), widget.direction(), step,
                    widget.steps());
            MenuLayout.Glyph glyph = glyph(label, new Image(shown, full.width(), full.height()), widget.x(),
                    widget.y());
            if (glyph == null) {
                return null;
            }
            frames.add(glyph);
        }
        return frames;
    }

    /** A bar drawn from its colour: a filled rectangle with a lighter top-left edge. */
    static Image generated(MenuArt.Widget widget) {
        int w = widget.width();
        int h = widget.height();
        int fill = 0xFF000000 | widget.color();
        int r = (widget.color() >> 16) & 0xFF;
        int g = (widget.color() >> 8) & 0xFF;
        int b = widget.color() & 0xFF;
        int light = 0xFF000000 | ((r + (255 - r) / 3) << 16) | ((g + (255 - g) / 3) << 8) | (b + (255 - b) / 3);
        int[] argb = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                argb[y * w + x] = (x == 0 || y == 0) && w > 2 && h > 2 ? light : fill;
            }
        }
        return new Image(argb, w, h);
    }

    private @Nullable Image texture(String label, PrismKey key) {
        String path = "assets/" + key.namespace() + "/textures/" + key.value() + ".png";
        byte[] png = files.get(path);
        if (png == null) {
            warnings.add(label + ": no texture " + path + "; it is not drawn (Bedrock and fallback items are"
                    + " unaffected)");
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                warnings.add(label + ": " + path + " is not a PNG image; it is not drawn");
                return null;
            }
            int w = image.getWidth();
            int h = image.getHeight();
            int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
            return new Image(argb, w, h);
        } catch (IOException | RuntimeException e) {
            warnings.add(label + ": cannot read " + path + " (" + e.getMessage() + "); it is not drawn");
            return null;
        }
    }

    /** The glyph that draws {@code image} with its top-left corner at ({@code x}, {@code y}); null if too large. */
    private @Nullable MenuLayout.Glyph glyph(String label, Image image, int x, int y) {
        int pad = MenuGlyphs.padding(y, image.height());
        int height = image.height() + pad;
        if (image.width() > MenuArt.MAX_GLYPH_SIZE || height > MenuArt.MAX_GLYPH_SIZE) {
            warnings.add(label + ": " + image.width() + "x" + height + " pixels" + (pad > 0 ? " (with " + pad
                    + " rows of padding below)" : "") + " is larger than the " + MenuArt.MAX_GLYPH_SIZE + "x"
                    + MenuArt.MAX_GLYPH_SIZE + " a font glyph may be; it is not drawn");
            return null;
        }
        int[] argb = new int[image.width() * height];
        System.arraycopy(image.argb(), 0, argb, 0, image.argb().length);
        byte[] png = GaugeIcons.encode(argb, image.width(), height);
        String hash = sha1(png).substring(0, 16);
        int ascent = MenuGlyphs.ascent(y);
        String id = hash + "@" + ascent;
        String text = chars.get(id);
        if (text == null) {
            if (next > MenuGlyphs.GLYPH_LAST) {
                warnings.add(label + ": the menu font is full (" + (MenuGlyphs.GLYPH_LAST - MenuGlyphs.GLYPH_BASE + 1)
                        + " glyphs); it is not drawn");
                return null;
            }
            text = String.valueOf((char) next++);
            chars.put(id, text);
            files.put(TEXTURE_DIR + hash + ".png", png);
            providers.add(new Provider("prism:font/menu/" + hash + ".png", ascent, height, text));
        }
        return new MenuLayout.Glyph(text, x, MenuGlyphs.advance(argb, image.width(), height));
    }

    private String font() {
        List<Object> list = new ArrayList<>();
        Map<String, Object> space = new LinkedHashMap<>();
        space.put("type", "space");
        space.put("advances", new LinkedHashMap<String, Object>(MenuGlyphs.shiftAdvances()));
        list.add(space);
        for (Provider provider : providers) {
            Map<String, Object> bitmap = new LinkedHashMap<>();
            bitmap.put("type", "bitmap");
            bitmap.put("file", provider.file());
            bitmap.put("ascent", provider.ascent());
            bitmap.put("height", provider.height());
            bitmap.put("chars", List.of(provider.chars()));
            list.add(bitmap);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("providers", list);
        return JsonWriter.write(root);
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
