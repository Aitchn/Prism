package com.aitchn.prism.api.platform;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * What Prism detected about the server. Registered as a Bukkit service by the Prism plugin.
 *
 * @param kind             platform family
 * @param brand            the server's self-reported name, e.g. {@code Paper} or {@code Purpur}
 * @param minecraftVersion Minecraft version of the running server
 * @param features         capabilities derived from {@code kind} and {@code minecraftVersion}
 */
public record PlatformInfo(
        @NotNull PlatformKind kind,
        @NotNull String brand,
        @NotNull MinecraftVersion minecraftVersion,
        @NotNull FeatureMatrix features) {

    public PlatformInfo {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(brand, "brand");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(features, "features");
    }

    public static @NotNull PlatformInfo of(
            @NotNull PlatformKind kind, @NotNull String brand, @NotNull MinecraftVersion version) {
        return new PlatformInfo(kind, brand, version, FeatureMatrix.of(version, kind));
    }

    public boolean isRegionized() {
        return kind.isRegionized();
    }
}
