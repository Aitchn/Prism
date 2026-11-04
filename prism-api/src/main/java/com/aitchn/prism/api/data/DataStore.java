package com.aitchn.prism.api.data;

import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Persistent key-value storage private to one namespace. Values are strings (store JSON or your own format).
 * Thread-safe, but calls block on disk: do not call from a hot path on the main or a region thread for large
 * volumes; batch your writes or hand them to an async task.
 */
public interface DataStore {

    @NotNull Optional<String> get(@NotNull String key);

    void put(@NotNull String key, @NotNull String value);

    /** @return true when the key existed */
    boolean remove(@NotNull String key);

    /** Keys starting with {@code prefix} (empty prefix: all keys), sorted. */
    @NotNull List<String> keys(@NotNull String prefix);
}
