package com.aitchn.prism.api;

import java.util.Objects;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Identity of anything Prism registers: {@code namespace:value}. The namespace is {@code [a-z0-9._-]+} and the value
 * is {@code [a-z0-9._/-]+} (slashes allow folders). The namespaces {@code prism} and {@code minecraft} are reserved.
 */
public record PrismKey(@NotNull String namespace, @NotNull String value) implements Comparable<PrismKey> {

    public static final String PRISM_NAMESPACE = "prism";
    public static final String MINECRAFT_NAMESPACE = "minecraft";

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern VALUE = Pattern.compile("[a-z0-9._/-]+");

    public PrismKey {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(value, "value");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid namespace '" + namespace + "', expected [a-z0-9._-]+");
        }
        if (!VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid key value '" + value + "', expected [a-z0-9._/-]+");
        }
    }

    /** Parses {@code namespace:value}; throws {@link IllegalArgumentException} when malformed. */
    public static @NotNull PrismKey parse(@NotNull String text) {
        Objects.requireNonNull(text, "text");
        int colon = text.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Key '" + text + "' must look like namespace:value");
        }
        return new PrismKey(text.substring(0, colon), text.substring(colon + 1));
    }

    /** Parses {@code text}; a bare {@code value} gets {@code defaultNamespace}. */
    public static @NotNull PrismKey parse(@NotNull String text, @NotNull String defaultNamespace) {
        return text.indexOf(':') < 0 ? new PrismKey(defaultNamespace, text) : parse(text);
    }

    public boolean isReserved() {
        return namespace.equals(PRISM_NAMESPACE) || namespace.equals(MINECRAFT_NAMESPACE);
    }

    @Override
    public int compareTo(@NotNull PrismKey other) {
        int byNamespace = namespace.compareTo(other.namespace);
        return byNamespace != 0 ? byNamespace : value.compareTo(other.value);
    }

    @Override
    public @NotNull String toString() {
        return namespace + ':' + value;
    }
}
