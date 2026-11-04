package com.aitchn.prism.core.registry;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.api.registry.RegistrySnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Registry with owner-bound, transactional writes. Readers never lock: every write replaces an immutable map, so a
 * {@link #snapshot()} is just the map that was current.
 */
public final class MutableRegistry<T> implements Registry<T> {

    private record Entry<T>(String owner, T value) {}

    private volatile Map<PrismKey, Entry<T>> entries = Map.of();

    /** Adds every entry for {@code owner} or none: a duplicate key leaves the registry untouched. */
    public synchronized void publish(@NotNull String owner, @NotNull Map<PrismKey, ? extends T> batch) {
        Objects.requireNonNull(owner, "owner");
        Map<PrismKey, Entry<T>> next = new HashMap<>(entries);
        for (Map.Entry<PrismKey, ? extends T> added : batch.entrySet()) {
            Entry<T> previous = next.putIfAbsent(added.getKey(), new Entry<>(owner, added.getValue()));
            if (previous != null) {
                throw new IllegalStateException(
                        added.getKey() + " is already registered by " + previous.owner());
            }
        }
        entries = Map.copyOf(next);
    }

    /** Removes everything {@code owner} registered. Returns how many entries were removed. */
    public synchronized int unregisterOwner(@NotNull String owner) {
        Map<PrismKey, Entry<T>> next = new HashMap<>(entries);
        int before = next.size();
        next.values().removeIf(entry -> entry.owner().equals(owner));
        entries = Map.copyOf(next);
        return before - next.size();
    }

    public @NotNull Optional<String> ownerOf(@NotNull PrismKey key) {
        return Optional.ofNullable(entries.get(key)).map(Entry::owner);
    }

    @Override
    public @NotNull Optional<T> get(@NotNull PrismKey key) {
        return Optional.ofNullable(entries.get(key)).map(Entry::value);
    }

    @Override
    public @NotNull Set<PrismKey> keys() {
        return entries.keySet();
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public @NotNull RegistrySnapshot<T> snapshot() {
        return new Snapshot<>(entries);
    }

    private record Snapshot<T>(Map<PrismKey, Entry<T>> entries) implements RegistrySnapshot<T> {
        @Override
        public @NotNull Optional<T> get(@NotNull PrismKey key) {
            return Optional.ofNullable(entries.get(key)).map(Entry::value);
        }

        @Override
        public @NotNull Set<PrismKey> keys() {
            return entries.keySet();
        }

        @Override
        public int size() {
            return entries.size();
        }
    }
}
