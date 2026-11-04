package com.aitchn.prism.api.platform;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * A Minecraft version such as {@code 1.21.4} or {@code 26.3}. The first component is called {@code major} even
 * for the legacy {@code 1.x} scheme, so ordering is simply component-wise and {@code 26.3 > 1.21.11}.
 */
public record MinecraftVersion(int major, int minor, int patch) implements Comparable<MinecraftVersion> {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    public MinecraftVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Negative version component: " + major + "." + minor + "." + patch);
        }
    }

    /**
     * Parses the leading {@code a.b[.c]} of {@code text}; anything after it (for example {@code -pre1}) is ignored.
     *
     * @throws IllegalArgumentException if {@code text} does not start with at least {@code a.b}
     */
    public static @NotNull MinecraftVersion parse(@NotNull String text) {
        Matcher m = PATTERN.matcher(text.trim());
        if (!m.find()) {
            throw new IllegalArgumentException("Not a Minecraft version: '" + text + "'");
        }
        return new MinecraftVersion(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                m.group(3) == null ? 0 : Integer.parseInt(m.group(3)));
    }

    public boolean isAtLeast(@NotNull MinecraftVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(@NotNull MinecraftVersion o) {
        int c = Integer.compare(major, o.major);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(minor, o.minor);
        return c != 0 ? c : Integer.compare(patch, o.patch);
    }

    @Override
    public @NotNull String toString() {
        return patch == 0 ? major + "." + minor : major + "." + minor + "." + patch;
    }
}
