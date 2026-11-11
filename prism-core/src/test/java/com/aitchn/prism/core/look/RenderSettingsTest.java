package com.aitchn.prism.core.look;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.content.PrismComponents.Render;
import org.junit.jupiter.api.Test;

class RenderSettingsTest {

    @Test
    void nothingSetMeansDisplayFirstWithOneEntityPerBlockAndNoNotes() {
        RenderSettings settings = RenderSettings.read(null, null, null);
        assertEquals(Render.DISPLAY, settings.defaultRender());
        assertEquals(DisplayLighting.BLOCK, settings.lighting());
        assertTrue(settings.notes().isEmpty());
    }

    @Test
    void renderWins() {
        assertEquals(Render.STATE, RenderSettings.read(null, "state", null).defaultRender());
        assertEquals(Render.DISPLAY, RenderSettings.read(null, "DISPLAY", null).defaultRender());
        RenderSettings both = RenderSettings.read("native", "display", null);
        assertEquals(Render.DISPLAY, both.defaultRender());
        assertEquals(1, both.notes().size(), "an ignored backend is logged");
        assertTrue(RenderSettings.read("auto", "state", null).notes().isEmpty(),
                "the old default backend next to the new key needs no note");
    }

    @Test
    void oldBackendValuesMapWithANote() {
        RenderSettings auto = RenderSettings.read("auto", null, null);
        assertEquals(Render.DISPLAY, auto.defaultRender());
        assertEquals(1, auto.notes().size());
        RenderSettings nativeStates = RenderSettings.read("native", null, null);
        assertEquals(Render.STATE, nativeStates.defaultRender());
        assertEquals(1, nativeStates.notes().size());
        RenderSettings display = RenderSettings.read("display", null, null);
        assertEquals(Render.DISPLAY, display.defaultRender());
        assertEquals(1, display.notes().size());
        RenderSettings unknown = RenderSettings.read("fancy", null, null);
        assertEquals(Render.DISPLAY, unknown.defaultRender());
        assertEquals(1, unknown.notes().size());
    }

    @Test
    void lightingIsReadAndBadValuesFallBackToBlock() {
        assertEquals(DisplayLighting.FACES, RenderSettings.read(null, null, "faces").lighting());
        assertEquals(DisplayLighting.BLOCK, RenderSettings.read(null, null, "block").lighting());
        RenderSettings bad = RenderSettings.read(null, "sideways", "sideways");
        assertEquals(DisplayLighting.BLOCK, bad.lighting());
        assertEquals(Render.DISPLAY, bad.defaultRender());
        assertEquals(2, bad.notes().size());
    }

    @Test
    void autoResolvesToTheDefaultAndExplicitPreferencesStay() {
        RenderSettings states = RenderSettings.read(null, "state", null);
        assertEquals(Render.STATE, states.resolve(Render.AUTO));
        assertEquals(Render.DISPLAY, states.resolve(Render.DISPLAY));
        assertEquals(Render.DISPLAY, RenderSettings.DEFAULT.resolve(Render.AUTO));
        assertEquals(Render.STATE, RenderSettings.DEFAULT.resolve(Render.STATE));
    }
}
