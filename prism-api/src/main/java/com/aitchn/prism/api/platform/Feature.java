package com.aitchn.prism.api.platform;

import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A server capability that exists only on some Minecraft versions or platforms. Prism code and mods ask
 * {@link FeatureMatrix#supports(Feature)} instead of comparing version numbers.
 */
public enum Feature {
    /** Data components on items (1.20.5+). */
    DATA_COMPONENTS(new MinecraftVersion(1, 20, 5), null),
    /** Several server resource packs per client (1.20.3+). */
    MULTIPLE_RESOURCE_PACKS(new MinecraftVersion(1, 20, 3), null),
    /** The {@code equippable} item component (1.21.2+). */
    EQUIPPABLE_COMPONENT(new MinecraftVersion(1, 21, 2), null),
    /** The {@code item_model} item component (1.21.4+); Prism's custom item rendering relies on it. */
    ITEM_MODEL_COMPONENT(new MinecraftVersion(1, 21, 4), null),
    /** World access is split across region threads. */
    REGIONIZED_THREADING(new MinecraftVersion(0, 0, 0), PlatformKind.FOLIA);

    private final MinecraftVersion minimumVersion;
    private final @Nullable PlatformKind requiredPlatform;

    Feature(MinecraftVersion minimumVersion, @Nullable PlatformKind requiredPlatform) {
        this.minimumVersion = minimumVersion;
        this.requiredPlatform = requiredPlatform;
    }

    /** First Minecraft version that has this feature. */
    public @NotNull MinecraftVersion minimumVersion() {
        return minimumVersion;
    }

    /** The only platform that offers this feature, if it is platform specific. */
    public @NotNull Optional<PlatformKind> requiredPlatform() {
        return Optional.ofNullable(requiredPlatform);
    }

    boolean isSupportedOn(@NotNull MinecraftVersion version, @NotNull PlatformKind platform) {
        return version.isAtLeast(minimumVersion) && (requiredPlatform == null || requiredPlatform == platform);
    }
}
