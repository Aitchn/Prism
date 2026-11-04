package com.aitchn.prism.core.content;

import org.jetbrains.annotations.NotNull;

/** One validation finding. {@code location} is a file path plus an optional in-file path. */
public record Problem(@NotNull String location, @NotNull String message) {

    @Override
    public @NotNull String toString() {
        return location.isEmpty() ? message : location + ": " + message;
    }
}
