package com.aitchn.prism.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.core.menu.MenuArt;
import com.aitchn.prism.core.menu.MenuGlyphs;
import com.aitchn.prism.core.menu.MenuLayout;
import com.aitchn.prism.core.menu.MenuTitles;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MenuArtPackTest {

    private static byte[] png(int width, int height, int argb) {
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, argb);
        return GaugeIcons.encode(pixels, width, height);
    }

    /** An arrow 8 wide whose last two columns are clear. */
    private static byte[] arrow() {
        int[] pixels = new int[8 * 4];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 6; x++) {
                pixels[y * 8 + x] = 0xFFE0B030;
            }
        }
        return GaugeIcons.encode(pixels, 8, 4);
    }

    private static MenuArt.Widget progress(String id, int x, int y, int steps) {
        return new MenuArt.Widget(id, MenuArt.Kind.PROGRESS, x, y, MenuArt.Direction.RIGHT, steps,
                List.of(PrismKey.parse("m:gui/arrow")), -1, 0, 0, -1, List.of());
    }

    private static MenuArt.Widget bar(String id, int steps) {
        return new MenuArt.Widget(id, MenuArt.Kind.BAR, 160, 18, MenuArt.Direction.UP, steps, List.of(), 0x30C0E0, 6,
                20, -1, List.of());
    }

    private static MenuArt menu(String key, String background, List<MenuArt.Widget> widgets) {
        return new MenuArt(PrismKey.parse(key), background == null ? null : PrismKey.parse(background), 3, 8, true,
                Set.of(), widgets);
    }

    private static Map<String, byte[]> files() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("assets/m/textures/gui/machine.png", png(176, 71, 0xFFC6C6C6));
        files.put("assets/m/textures/gui/arrow.png", arrow());
        return files;
    }

    @Test
    void writesTheFontAndOneTexturePerGlyph() throws Exception {
        Map<String, byte[]> files = files();
        List<String> warnings = new ArrayList<>();
        MenuArtPack.Result result = MenuArtPack.add(files, warnings,
                List.of(menu("m:machine", "m:gui/machine", List.of(progress("arrow", 62, 36, 4), bar("energy", 4)))));
        assertEquals(List.of(), warnings);
        assertEquals(1, result.backgrounds());
        assertEquals(2, result.widgets());
        assertEquals(3 + 4, result.steps()); // the arrow's last two steps are the same picture
        assertEquals(1 + 3 + 4, result.glyphs());
        String font = new String(files.get(MenuArtPack.FONT_PATH), StandardCharsets.UTF_8);
        assertTrue(font.contains("\"type\": \"space\"") || font.contains("\"type\":\"space\""), font);
        long textures = files.keySet().stream().filter(path -> path.startsWith(MenuArtPack.TEXTURE_DIR)).count();
        assertEquals(8, textures);

        MenuLayout layout = result.layouts().get(PrismKey.parse("m:machine"));
        assertNotNull(layout.background());
        assertEquals(0, layout.background().x());
        assertEquals(177, layout.background().advance()); // 176 drawn columns + 1
        assertTrue(font.contains("\"ascent\": 13") || font.contains("\"ascent\":13"), font);
        assertTrue(font.contains(layout.background().text()));
        // The arrow's step glyphs: revealed 2, 4, 6 and 6 columns of 8 (the last two columns are clear).
        List<Integer> advances = layout.widgets().get("arrow").frames().stream().map(MenuLayout.Glyph::advance)
                .toList();
        assertEquals(List.of(3, 5, 7, 7), advances);
        // Steps 3 and 4 draw the same pixels at the same place: one glyph.
        List<MenuLayout.Glyph> frames = layout.widgets().get("arrow").frames();
        assertEquals(frames.get(2).text(), frames.get(3).text());
        Map<String, Integer> values = new LinkedHashMap<>(MenuTitles.initial(layout.art()));
        assertEquals(8, MenuTitles.penAfter(layout, values));
        values.put("arrow", 2);
        values.put("energy", 3);
        assertEquals(8, MenuTitles.penAfter(layout, values));
    }

    @Test
    void stepTexturesAreSlicesOfTheSprite() throws Exception {
        Map<String, byte[]> files = files();
        MenuArtPack.Result result = MenuArtPack.add(files, new ArrayList<>(),
                List.of(menu("m:machine", null, List.of(progress("arrow", 62, 40, 4)))));
        String font = new String(files.get(MenuArtPack.FONT_PATH), StandardCharsets.UTF_8);
        // Second provider (after the space provider) is step 1: 2 columns of 8 revealed.
        int first = font.indexOf("prism:font/menu/");
        String hash = font.substring(first + "prism:font/menu/".length(), font.indexOf(".png", first));
        BufferedImage step1 = ImageIO.read(new ByteArrayInputStream(files.get(MenuArtPack.TEXTURE_DIR + hash + ".png")));
        assertEquals(8, step1.getWidth());
        assertEquals(4, step1.getHeight()); // y 40: ascent -27, no padding
        assertEquals(0xFF, step1.getRGB(1, 0) >>> 24);
        assertEquals(0, step1.getRGB(2, 0) >>> 24);
        assertEquals(4, result.layouts().get(PrismKey.parse("m:machine")).widgets().get("arrow").frames().size());
    }

    @Test
    void glyphsAreSharedAcrossMenus() {
        Map<String, byte[]> files = files();
        MenuArtPack.Result result = MenuArtPack.add(files, new ArrayList<>(), List.of(
                menu("m:a", "m:gui/machine", List.of(progress("arrow", 62, 36, 4))),
                menu("m:b", "m:gui/machine", List.of(progress("arrow", 62, 36, 4))),
                menu("m:c", "m:gui/machine", List.of(progress("arrow", 62, 50, 4)))));
        MenuLayout a = result.layouts().get(PrismKey.parse("m:a"));
        MenuLayout b = result.layouts().get(PrismKey.parse("m:b"));
        MenuLayout c = result.layouts().get(PrismKey.parse("m:c"));
        assertEquals(a.background().text(), b.background().text());
        assertEquals(a.background().text(), c.background().text());
        assertEquals(a.widgets().get("arrow").frames(), b.widgets().get("arrow").frames());
        // Another height on the screen is another ascent: other characters, same textures.
        assertFalse(a.widgets().get("arrow").frames().getFirst().text()
                .equals(c.widgets().get("arrow").frames().getFirst().text()));
        assertEquals(1 + 3 + 3, result.glyphs());
        Set<String> textures = new HashSet<>();
        files.keySet().stream().filter(path -> path.startsWith(MenuArtPack.TEXTURE_DIR)).forEach(textures::add);
        assertEquals(1 + 3, textures.size());
    }

    @Test
    void smallPicturesNearTheTopArePadded() throws Exception {
        Map<String, byte[]> files = files();
        MenuArtPack.Result result = MenuArtPack.add(files, new ArrayList<>(),
                List.of(menu("m:top", null, List.of(progress("arrow", 100, 2, 1)))));
        String font = new String(files.get(MenuArtPack.FONT_PATH), StandardCharsets.UTF_8);
        assertTrue(font.contains("\"ascent\": 11") || font.contains("\"ascent\":11"), font);
        assertTrue(font.contains("\"height\": 11") || font.contains("\"height\":11"), font);
        assertEquals(1, result.layouts().get(PrismKey.parse("m:top")).widgets().get("arrow").frames().size());
    }

    @Test
    void missingAndOversizedTexturesAreWarnedAndLeftOut() {
        Map<String, byte[]> files = files();
        files.put("assets/m/textures/gui/huge.png", png(176, 300, 0xFF000000));
        List<String> warnings = new ArrayList<>();
        MenuArtPack.Result result = MenuArtPack.add(files, warnings, List.of(
                menu("m:huge", "m:gui/huge", List.of(progress("arrow", 62, 36, 2))),
                menu("m:lost", "m:gui/lost", List.of())));
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("larger than"), warnings.toString());
        assertTrue(warnings.get(1).contains("no texture assets/m/textures/gui/lost.png"), warnings.toString());
        assertNull(result.layouts().get(PrismKey.parse("m:huge")).background());
        assertNull(result.layouts().get(PrismKey.parse("m:lost")));
        assertEquals(0, result.backgrounds());
    }

    @Test
    void sameContentSameFont() {
        Map<String, byte[]> one = files();
        Map<String, byte[]> two = files();
        List<MenuArt> menus = List.of(menu("m:b", "m:gui/machine", List.of(bar("e", 8))),
                menu("m:a", null, List.of(progress("arrow", 62, 36, 4))));
        MenuArtPack.add(one, new ArrayList<>(), menus);
        MenuArtPack.add(two, new ArrayList<>(), List.of(menus.get(1), menus.get(0)));
        assertArrayEquals(one.get(MenuArtPack.FONT_PATH), two.get(MenuArtPack.FONT_PATH));
        assertTrue(MenuGlyphs.hasPrivateUse(new String(one.get(MenuArtPack.FONT_PATH), StandardCharsets.UTF_8)));
    }
}
