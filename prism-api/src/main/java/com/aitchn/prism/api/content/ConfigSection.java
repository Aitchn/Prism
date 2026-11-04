package com.aitchn.prism.api.content;

import com.aitchn.prism.api.PrismKey;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of one YAML mapping, handed to component decoders and behavior factories. Every {@code require*}
 * method throws a {@link ContentException} that already carries the full path, so decoders do not format errors.
 */
public interface ConfigSection {

    /** Dotted path from the file root, for error messages. */
    @NotNull String path();

    @NotNull Set<String> keys();

    boolean has(@NotNull String key);

    @NotNull Optional<String> getString(@NotNull String key) throws ContentException;

    @NotNull String requireString(@NotNull String key) throws ContentException;

    @NotNull Optional<Integer> getInt(@NotNull String key) throws ContentException;

    int requireInt(@NotNull String key) throws ContentException;

    @NotNull Optional<Double> getDouble(@NotNull String key) throws ContentException;

    double requireDouble(@NotNull String key) throws ContentException;

    boolean getBoolean(@NotNull String key, boolean fallback) throws ContentException;

    @NotNull Optional<ConfigSection> getSection(@NotNull String key) throws ContentException;

    @NotNull ConfigSection requireSection(@NotNull String key) throws ContentException;

    /** A list of mappings; a missing key is an empty list. */
    @NotNull List<ConfigSection> getSectionList(@NotNull String key) throws ContentException;

    /** A list of strings; a missing key is an empty list. */
    @NotNull List<String> getStringList(@NotNull String key) throws ContentException;

    @NotNull PrismKey requireKey(@NotNull String key) throws ContentException;

    /** Fails if the section has a key outside {@code allowed}; catches typos early. */
    void rejectUnknownKeys(@NotNull Set<String> allowed) throws ContentException;

    /** A {@link ContentException} located at this section. */
    @NotNull ContentException error(@NotNull String message);
}
