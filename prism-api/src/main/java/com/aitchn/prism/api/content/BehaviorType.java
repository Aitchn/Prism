package com.aitchn.prism.api.content;

import com.aitchn.prism.api.PrismKey;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** A kind of behavior YAML can bind with {@code behaviors: [{type: ns:name, ...}]}. */
public record BehaviorType<B extends Behavior>(
        @NotNull PrismKey key, @NotNull Class<B> behaviorType, @NotNull BehaviorFactory<B> factory) {

    public BehaviorType {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(behaviorType, "behaviorType");
        Objects.requireNonNull(factory, "factory");
    }
}
