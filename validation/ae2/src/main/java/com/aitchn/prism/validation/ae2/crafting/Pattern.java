package com.aitchn.prism.validation.ae2.crafting;

import com.aitchn.prism.validation.ae2.storage.ItemKey;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** An encoded recipe: consumes {@code inputs} once and yields {@code outputCount} of {@code output}. */
public record Pattern(
        @NotNull String id,
        @NotNull Map<ItemKey, Long> inputs,
        @NotNull ItemKey output,
        long outputCount) {

    public Pattern {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(output, "output");
        inputs = Map.copyOf(inputs);
        if (outputCount <= 0) {
            throw new IllegalArgumentException("a pattern must produce at least one item");
        }
        if (inputs.isEmpty() || inputs.values().stream().anyMatch(n -> n <= 0)) {
            throw new IllegalArgumentException("a pattern needs positive inputs");
        }
    }
}
