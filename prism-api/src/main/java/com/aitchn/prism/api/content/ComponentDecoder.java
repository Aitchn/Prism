package com.aitchn.prism.api.content;

import org.jetbrains.annotations.NotNull;

/** Turns a YAML section into an immutable component value. A scalar YAML value arrives as {@code {value: <scalar>}}. */
@FunctionalInterface
public interface ComponentDecoder<T> {

    @NotNull T decode(@NotNull ConfigSection section) throws ContentException;
}
