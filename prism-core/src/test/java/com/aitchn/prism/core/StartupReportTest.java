package com.aitchn.prism.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.platform.Feature;
import com.aitchn.prism.api.platform.MinecraftVersion;
import com.aitchn.prism.api.platform.PlatformInfo;
import com.aitchn.prism.api.platform.PlatformKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartupReportTest {

    private final PlatformInfo folia = PlatformInfo.of(PlatformKind.FOLIA, "Folia", MinecraftVersion.parse("26.3"));

    @Test
    void readyMarkerIsStable() {
        assertEquals("PRISM_READY platform=FOLIA minecraft=26.3 regionized=true", StartupReport.readyMarker(folia));
    }

    @Test
    void reportListsEveryFeature() {
        List<String> lines = StartupReport.render(folia, "0.1.0");
        assertTrue(lines.get(0).contains("Folia (FOLIA), Minecraft 26.3"));
        assertEquals(Feature.values().length + 2, lines.size());
        assertTrue(lines.contains("  [x] REGIONIZED_THREADING"));
    }
}
