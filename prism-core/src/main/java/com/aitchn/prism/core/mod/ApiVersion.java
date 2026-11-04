package com.aitchn.prism.core.mod;

import org.jetbrains.annotations.NotNull;

/** {@code major.minor} of the Prism API. A mod written for 0.2 does not run on 0.1; a different major never runs. */
public record ApiVersion(int major, int minor) {

    public static @NotNull ApiVersion parse(@NotNull String text) {
        String[] parts = text.trim().split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("API version must look like major.minor, got '" + text + "'");
        }
        try {
            return new ApiVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("API version must look like major.minor, got '" + text + "'");
        }
    }

    /** Whether code that needs {@code required} can run on this (the provided) version. */
    public boolean satisfies(@NotNull ApiVersion required) {
        return major == required.major && minor >= required.minor;
    }

    @Override
    public @NotNull String toString() {
        return major + "." + minor;
    }
}
