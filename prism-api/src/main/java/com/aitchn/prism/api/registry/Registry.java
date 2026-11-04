package com.aitchn.prism.api.registry;

import com.aitchn.prism.api.PrismKey;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Read-only view of registered values. A live registry reflects later changes; use {@link #snapshot()} to freeze. */
public interface Registry<T> {

    @NotNull Optional<T> get(@NotNull PrismKey key);

    /** The value for {@code key}, or an {@link IllegalArgumentException} naming the missing key. */
    default @NotNull T require(@NotNull PrismKey key) {
        return get(key).orElseThrow(() -> new IllegalArgumentException("Nothing registered as " + key));
    }

    default boolean contains(@NotNull PrismKey key) {
        return get(key).isPresent();
    }

    @NotNull Set<PrismKey> keys();

    int size();

    /** An immutable copy that stays consistent across several queries. */
    @NotNull RegistrySnapshot<T> snapshot();
}
