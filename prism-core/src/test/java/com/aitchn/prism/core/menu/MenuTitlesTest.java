package com.aitchn.prism.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.core.content.MapConfigSection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MenuTitlesTest {

    private static final MenuArt.Widget ARROW = new MenuArt.Widget("arrow", MenuArt.Kind.PROGRESS, 62, 36,
            MenuArt.Direction.RIGHT, 4, List.of(PrismKey.parse("m:gui/arrow")), -1, 0, 0, 12,
            List.of(PrismKey.parse("m:arrow_gauge")));
    private static final MenuArt.Widget LAMP = new MenuArt.Widget("lamp", MenuArt.Kind.ICON, 150, 5,
            MenuArt.Direction.RIGHT, 2, List.of(PrismKey.parse("m:gui/off"), PrismKey.parse("m:gui/on")), -1, 0, 0,
            -1, List.of());

    private static MenuLayout layout(boolean titleShown) {
        MenuArt art = new MenuArt(PrismKey.parse("m:machine"), PrismKey.parse("m:gui/machine"), 3, 30, titleShown,
                Set.of(11, 15), List.of(ARROW, LAMP));
        List<MenuLayout.Glyph> arrow = new ArrayList<>();
        for (int step = 1; step <= 4; step++) {
            arrow.add(new MenuLayout.Glyph(String.valueOf((char) (0xE101 + step)), 62, 2 + step * 5));
        }
        Map<String, MenuLayout.WidgetGlyphs> widgets = new LinkedHashMap<>();
        widgets.put("arrow", new MenuLayout.WidgetGlyphs(ARROW, arrow));
        widgets.put("lamp", new MenuLayout.WidgetGlyphs(LAMP, List.of(new MenuLayout.Glyph("", 150, 9),
                new MenuLayout.Glyph("", 150, 9))));
        return new MenuLayout(art, new MenuLayout.Glyph("", 0, 177), widgets);
    }

    @Test
    void javaTitleDrawsTheBackgroundAtTheOriginAndEndsAtTheTitle() {
        MenuLayout layout = layout(true);
        Map<String, Integer> values = MenuTitles.initial(layout.art());
        String glyphs = MenuTitles.glyphs(layout, values);
        // Back 8 to the left edge, the background, back to x 150 for the lamp (state 0), then to the title's x 30.
        assertEquals(MenuGlyphs.shift(-8) + "" + MenuGlyphs.shift(150 - 177) + ""
                + MenuGlyphs.shift(30 - 159), glyphs);
        assertEquals(30, MenuTitles.penAfter(layout, values));
        assertEquals(glyphs + "Machine", MenuTitles.java(layout, values, "Machine"));
    }

    @Test
    void titleChangesOnlyWhenADrawnStepChanges() {
        MenuLayout layout = layout(true);
        Map<String, Integer> values = new LinkedHashMap<>(MenuTitles.initial(layout.art()));
        String empty = MenuTitles.glyphs(layout, values);
        values.put("arrow", 2);
        String half = MenuTitles.glyphs(layout, values);
        assertNotEquals(empty, half);
        assertTrue(half.contains(""));
        assertEquals(30, MenuTitles.penAfter(layout, values));
        values.put("arrow", 99); // past the end: the last step
        assertTrue(MenuTitles.glyphs(layout, values).contains(""));
        values.put("lamp", 1);
        assertTrue(MenuTitles.glyphs(layout, values).contains(""));
        values.put("lamp", 7); // clamped to the last state
        assertTrue(MenuTitles.glyphs(layout, values).contains(""));
    }

    @Test
    void hiddenTitleLeavesTheTextOut() {
        MenuLayout layout = layout(false);
        Map<String, Integer> values = MenuTitles.initial(layout.art());
        assertEquals(MenuTitles.glyphs(layout, values), MenuTitles.java(layout, values, "Machine"));
        assertFalse(MenuTitles.java(layout, values, "Machine").contains("Machine"));
    }

    @Test
    void bedrockTitleIsTheTextWithoutGlyphs() {
        MenuLayout layout = layout(true);
        String java = MenuTitles.java(layout, MenuTitles.initial(layout.art()), "機器 Machine");
        assertTrue(MenuGlyphs.hasPrivateUse(java));
        assertEquals("機器 Machine", MenuTitles.bedrock(java));
        assertFalse(MenuGlyphs.hasPrivateUse(MenuTitles.bedrock(java)));
    }

    @Test
    void stepsAndStatesFollowTheGaugeRules() {
        assertEquals(0, ARROW.step(0));
        assertEquals(1, ARROW.step(0.01));
        assertEquals(3, ARROW.step(0.99));
        assertEquals(4, ARROW.step(1));
        assertEquals(0, LAMP.state(-3));
        assertEquals(1, LAMP.state(5));
        MenuLayout.WidgetGlyphs arrow = layout(true).widgets().get("arrow");
        assertNull(arrow.frame(0));
        assertEquals("", arrow.frame(1).text());
    }

    @Test
    void decodesAMenuFile() throws ContentException {
        Map<String, Object> energy = new LinkedHashMap<>();
        energy.put("type", "bar");
        energy.put("x", 8);
        energy.put("y", 18);
        energy.put("color", "#30c0e0");
        energy.put("width", 6);
        energy.put("height", 50);
        Map<String, Object> arrow = new LinkedHashMap<>();
        arrow.put("type", "progress");
        arrow.put("x", 62);
        arrow.put("y", 36);
        arrow.put("sprite", "gui/arrow");
        arrow.put("steps", 8);
        arrow.put("fallback", Map.of("slot", 12, "item", "arrow_gauge"));
        Map<String, Object> widgets = new LinkedHashMap<>();
        widgets.put("progress", arrow);
        widgets.put("energy", energy);
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("background", "gui/machine");
        file.put("rows", 3);
        file.put("title", Map.of("x", 30));
        file.put("slots", List.of(11, "13-15", 22));
        file.put("widgets", widgets);
        MenuArt art = MenuArt.decode(PrismKey.parse("m:machine"), MapConfigSection.of("", file));
        assertEquals(PrismKey.parse("m:gui/machine"), art.background());
        assertEquals(30, art.titleX());
        assertTrue(art.titleShown());
        assertEquals(Set.of(11, 13, 14, 15, 22), art.slots());
        assertTrue(art.hidden(12));
        assertFalse(art.hidden(14));
        assertEquals(List.of("progress", "energy"), art.widgets().stream().map(MenuArt.Widget::id).toList());
        MenuArt.Widget bar = art.widget("energy");
        assertEquals(MenuArt.Direction.UP, bar.direction());
        assertEquals(MenuArt.DEFAULT_STEPS, bar.steps());
        assertEquals(0x30c0e0, bar.color());
        assertEquals(12, art.widget("progress").fallbackSlot());
        assertEquals(PrismKey.parse("m:arrow_gauge"), art.widget("progress").fallbackItems().getFirst());
    }

    @Test
    void rejectsBadMenuFiles() {
        Map<String, Object> tooMany = new LinkedHashMap<>();
        tooMany.put("type", "progress");
        tooMany.put("x", 0);
        tooMany.put("y", 0);
        tooMany.put("sprite", "a");
        tooMany.put("steps", MenuArt.MAX_STEPS + 1);
        assertThrows(ContentException.class, () -> MenuArt.decode(PrismKey.parse("m:a"),
                MapConfigSection.of("", Map.of("widgets", Map.of("w", tooMany)))));
        assertThrows(ContentException.class, () -> MenuArt.decode(PrismKey.parse("m:a"),
                MapConfigSection.of("", Map.of("rows", 3))));
        assertThrows(ContentException.class, () -> MenuArt.decode(PrismKey.parse("m:a"),
                MapConfigSection.of("", Map.of("background", "b", "slots", List.of("20-40")))));
        assertThrows(ContentException.class, () -> MenuArt.decode(PrismKey.parse("m:a"),
                MapConfigSection.of("", Map.of("background", "b", "colour", "x"))));
        Map<String, Object> noSprite = new LinkedHashMap<>();
        noSprite.put("type", "bar");
        noSprite.put("x", 0);
        noSprite.put("y", 0);
        assertThrows(ContentException.class, () -> MenuArt.decode(PrismKey.parse("m:a"),
                MapConfigSection.of("", Map.of("widgets", Map.of("w", noSprite)))));
    }
}
