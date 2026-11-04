package com.aitchn.prism.api.content;

import com.aitchn.prism.api.PrismKey;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** A kind of immutable data attached to a definition, declared in YAML under {@code components:}. */
public record ComponentType<T>(@NotNull PrismKey key, @NotNull Class<T> valueType, @NotNull ComponentDecoder<T> decoder) {

    public ComponentType {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(valueType, "valueType");
        Objects.requireNonNull(decoder, "decoder");
    }
}
