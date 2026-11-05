package com.aitchn.prism.validation.ae2.storage;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * What the network treats as "the same item". The id is opaque to the grid logic: the Bukkit adapter uses a
 * canonical serialization of the stack so enchantments and custom data keep items apart.
 */
public record ItemKey(@NotNull String id) implements Comparable<ItemKey> {

    public ItemKey {
        Objects.requireNonNull(id, "id");
        if (id.isEmpty() || id.indexOf('\t') >= 0 || id.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("item id must be non-empty and free of tabs and newlines");
        }
    }

    @Override
    public int compareTo(@NotNull ItemKey other) {
        return id.compareTo(other.id);
    }

    @Override
    public @NotNull String toString() {
        return id;
    }
}
