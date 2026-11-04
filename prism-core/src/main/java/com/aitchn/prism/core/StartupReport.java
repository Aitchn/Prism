package com.aitchn.prism.core;

import com.aitchn.prism.api.platform.Feature;
import com.aitchn.prism.api.platform.PlatformInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** Renders the platform banner and feature matrix that Prism prints on startup. */
public final class StartupReport {

    /** Machine-readable line the Docker smoke test greps for. Keep the format stable. */
    public static final String READY_MARKER = "PRISM_READY";

    private StartupReport() {}

    public static @NotNull List<String> render(@NotNull PlatformInfo info, @NotNull String prismVersion) {
        List<String> lines = new ArrayList<>();
        lines.add("Prism " + prismVersion + " running on " + info.brand() + " (" + info.kind() + "), Minecraft "
                + info.minecraftVersion() + ", regionized=" + info.isRegionized());
        lines.add("Feature matrix:");
        for (Map.Entry<Feature, Boolean> e : info.features().asMap().entrySet()) {
            lines.add("  " + (e.getValue() ? "[x] " : "[ ] ") + e.getKey());
        }
        return lines;
    }

    /** The single-line marker: {@code PRISM_READY platform=FOLIA minecraft=26.3 regionized=true}. */
    public static @NotNull String readyMarker(@NotNull PlatformInfo info) {
        return READY_MARKER + " platform=" + info.kind() + " minecraft=" + info.minecraftVersion()
                + " regionized=" + info.isRegionized();
    }
}
