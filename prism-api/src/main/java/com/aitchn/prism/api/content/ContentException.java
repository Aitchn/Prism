package com.aitchn.prism.api.content;

import org.jetbrains.annotations.NotNull;

/** A problem with mod content. {@link #path()} says where (for example {@code items/ruby.yml#components.mod:food}). */
public final class ContentException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String path;

    public ContentException(@NotNull String path, @NotNull String message) {
        super(message);
        this.path = path;
    }

    public ContentException(@NotNull String path, @NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
        this.path = path;
    }

    public @NotNull String path() {
        return path;
    }

    @Override
    public String getMessage() {
        return path.isEmpty() ? super.getMessage() : path + ": " + super.getMessage();
    }
}
