package com.aitchn.prism.api.registry;

import org.jetbrains.annotations.NotNull;

/** An immutable {@link Registry}: every query sees the same entries. */
public interface RegistrySnapshot<T> extends Registry<T> {

    @Override
    default @NotNull RegistrySnapshot<T> snapshot() {
        return this;
    }
}
