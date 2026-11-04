package com.aitchn.prism.api.content;

import com.aitchn.prism.api.PrismKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Immutable, type-safe set of decoded components. */
public final class ComponentMap {

    private static final ComponentMap EMPTY = new ComponentMap(Map.of());

    private final Map<PrismKey, Object> values;

    private ComponentMap(Map<PrismKey, Object> values) {
        this.values = values;
    }

    public static @NotNull ComponentMap empty() {
        return EMPTY;
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public <T> @NotNull Optional<T> get(@NotNull ComponentType<T> type) {
        Object value = values.get(type.key());
        return value == null ? Optional.empty() : Optional.of(type.valueType().cast(value));
    }

    public boolean has(@NotNull ComponentType<?> type) {
        return values.containsKey(type.key());
    }

    public @NotNull Set<PrismKey> keys() {
        return values.keySet();
    }

    public int size() {
        return values.size();
    }

    public static final class Builder {
        private final Map<PrismKey, Object> values = new LinkedHashMap<>();

        public <T> @NotNull Builder put(@NotNull ComponentType<T> type, @NotNull T value) {
            values.put(type.key(), type.valueType().cast(value));
            return this;
        }

        public @NotNull ComponentMap build() {
            return values.isEmpty() ? EMPTY : new ComponentMap(Map.copyOf(values));
        }
    }
}
