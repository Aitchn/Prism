package com.aitchn.prism.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ComponentMap;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class GaugeIconsTest {

    private static final PrismComponents.Gauge BAR = new PrismComponents.Gauge("bar", 0xD03030, 4);
    private static final PrismComponents.Gauge ARROW = new PrismComponents.Gauge("arrow", 0xE0B030, 4);

    private static ItemDefinition gauge(PrismComponents.Gauge gauge) {
        return new ItemDefinition(PrismKey.parse("m:energy"), "mod", PrismKey.parse("minecraft:paper"),
                ComponentMap.builder().put(PrismComponents.GAUGE, gauge).build(), List.of());
    }

    @Test
    void stepsKeepTheEndsForEmptyAndFull() {
        assertEquals(0, BAR.step(0));
        assertEquals(0, BAR.step(-1));
        assertEquals(1, BAR.step(0.01));
        assertEquals(2, BAR.step(0.5));
        assertEquals(3, BAR.step(0.99));
        assertEquals(4, BAR.step(1));
        assertEquals(4, BAR.step(7));
        assertEquals(0, BAR.step(Double.NaN));
        assertEquals(1, new PrismComponents.Gauge("bar", 0, 1).step(0.5));
    }

    @Test
    void iconsAreValidDeterministicAndFillUp() {
        byte[] png = GaugeIcons.png(BAR, 2);
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'}, Arrays.copyOf(png, 4));
        assertArrayEquals(png, GaugeIcons.png(BAR, 2));
        for (PrismComponents.Gauge gauge : List.of(BAR, ARROW)) {
            long previous = -1;
            for (int step = 0; step <= gauge.steps(); step++) {
                int[] pixels = GaugeIcons.pixels(gauge, step);
                long filled = Arrays.stream(pixels).filter(p -> (p & 0xFFFFFF) == gauge.color()).count();
                assertTrue(filled > previous || step == 0, gauge.style() + " step " + step);
                previous = filled;
            }
            assertEquals(0, Arrays.stream(GaugeIcons.pixels(gauge, 0))
                    .filter(p -> (p & 0xFFFFFF) == gauge.color()).count());
        }
        assertNotEquals(Arrays.hashCode(GaugeIcons.png(BAR, 1)), Arrays.hashCode(GaugeIcons.png(BAR, 3)));
    }

    @Test
    void middleStepsNeverLookEmptyOrFullAtTheMostSteps() {
        for (String style : PrismComponents.Gauge.STYLES) {
            var gauge = new PrismComponents.Gauge(style, 0x30D030, 32);
            int[] empty = GaugeIcons.pixels(gauge, 0);
            int[] full = GaugeIcons.pixels(gauge, 32);
            for (int step = 1; step < 32; step++) {
                int[] pixels = GaugeIcons.pixels(gauge, step);
                assertFalse(Arrays.equals(empty, pixels), style + " step " + step + " looks empty");
                assertFalse(Arrays.equals(full, pixels), style + " step " + step + " looks full");
            }
        }
    }

    @Test
    void javaPackGetsOneDefinitionPerStepAndKeepsShippedTextures() {
        byte[] own = {1, 2, 3};
        Map<String, byte[]> assets = new LinkedHashMap<>();
        assets.put("assets/m/textures/item/energy/gauge_4.png", own);
        var result = JavaPackBuilder.resolve(new JavaPackBuilder.Input(Map.of("mod", assets), List.of(gauge(BAR)),
                List.of(), 46, "test"));
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
        assertTrue(result.itemsWithAppearance().contains(PrismKey.parse("m:energy")));
        for (int step = 0; step <= 4; step++) {
            assertTrue(result.files().containsKey("assets/m/items/energy/gauge_" + step + ".json"), "step " + step);
            assertTrue(result.files().containsKey("assets/m/textures/item/energy/gauge_" + step + ".png"));
        }
        assertArrayEquals(own, result.files().get("assets/m/textures/item/energy/gauge_4.png"));
        String self = new String(result.files().get("assets/m/items/energy.json"), StandardCharsets.UTF_8);
        assertTrue(self.contains("m:item/energy/gauge_4"), self);
    }

    @Test
    void bedrockPackMapsEveryStep() throws Exception {
        var result = BedrockPackBuilder.build(new BedrockPackBuilder.Input(Map.of("mod", Map.of()),
                List.of(gauge(ARROW)), "test"));
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
        assertEquals(5, result.gaugeIcons());
        assertTrue(result.itemsWithIcon().contains(PrismKey.parse("m:energy")));
        for (int step = 0; step <= 4; step++) {
            assertTrue(result.mappings().contains("\"model\": \"m:energy/gauge_" + step + "\""), result.mappings());
            assertTrue(result.mappings().contains("\"bedrock_identifier\": \"m:energy_gauge_" + step + "\""));
        }
        assertTrue(result.mappings().contains("\"model\": \"m:energy\""));
        boolean found = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(result.mcpack()))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                found |= entry.getName().equals("textures/prism/m/energy_gauge_2.png");
            }
        }
        assertTrue(found);
        assertFalse(result.mappings().contains("m:energy/gauge_5"));
    }
}
