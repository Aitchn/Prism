package com.aitchn.prism.api.platform;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** Immutable answer to "which {@link Feature}s does this server have?". */
public final class FeatureMatrix {

    private final Map<Feature, Boolean> supported;

    private FeatureMatrix(Map<Feature, Boolean> supported) {
        this.supported = Collections.unmodifiableMap(supported);
    }

    public static @NotNull FeatureMatrix of(@NotNull MinecraftVersion version, @NotNull PlatformKind platform) {
        Map<Feature, Boolean> map = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            map.put(feature, feature.isSupportedOn(version, platform));
        }
        return new FeatureMatrix(map);
    }

    public boolean supports(@NotNull Feature feature) {
        return supported.get(feature);
    }

    /** Every feature in declaration order with its support flag. */
    public @NotNull Map<Feature, Boolean> asMap() {
        return supported;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FeatureMatrix other && supported.equals(other.supported);
    }

    @Override
    public int hashCode() {
        return supported.hashCode();
    }

    @Override
    public @NotNull String toString() {
        return "FeatureMatrix" + supported;
    }
}
