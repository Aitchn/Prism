package com.aitchn.prism.api.block;

import com.aitchn.prism.api.PrismKey;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * A named kind of service a block can offer to its neighbours, such as an item inventory or a mod's own energy
 * storage. Prism defines only the lookup mechanism and a few vanilla-backed capabilities ({@link PrismCapabilities});
 * what a capability means (units, rules) is up to the mod that declares it. Two capabilities are the same when their
 * keys are equal, so mods that agree on a key and an interface can talk to each other.
 *
 * @param type the interface a provider returns
 */
public record BlockCapability<T>(@NotNull PrismKey key, @NotNull Class<T> type) {

    public BlockCapability {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
    }

    public static <T> @NotNull BlockCapability<T> of(@NotNull PrismKey key, @NotNull Class<T> type) {
        return new BlockCapability<>(key, type);
    }
}
