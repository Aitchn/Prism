package com.aitchn.prism.core.content;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** {@link ConfigSection} over a parsed YAML map. */
public final class MapConfigSection implements ConfigSection {

    private final String path;
    private final Map<String, Object> values;

    public MapConfigSection(@NotNull String path, @NotNull Map<String, Object> values) {
        this.path = Objects.requireNonNull(path, "path");
        this.values = Objects.requireNonNull(values, "values");
    }

    /** Converts a YAML node that must be a mapping; keys are stringified. */
    public static @NotNull MapConfigSection of(@NotNull String path, Object node) throws ContentException {
        if (!(node instanceof Map<?, ?> map)) {
            throw new ContentException(path, "expected a mapping, got " + describe(node));
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return new MapConfigSection(path, copy);
    }

    /** Raw value, for the loader. */
    Object value(String key) {
        return values.get(key);
    }

    /** A copy without {@code key}. */
    public @NotNull MapConfigSection without(@NotNull String key) {
        Map<String, Object> copy = new LinkedHashMap<>(values);
        copy.remove(key);
        return new MapConfigSection(path, copy);
    }

    @Override
    public @NotNull String path() {
        return path;
    }

    @Override
    public @NotNull Set<String> keys() {
        return values.keySet();
    }

    @Override
    public boolean has(@NotNull String key) {
        return values.get(key) != null;
    }

    @Override
    public @NotNull Optional<String> getString(@NotNull String key) throws ContentException {
        Object value = values.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return Optional.of(String.valueOf(value));
        }
        throw typeError(key, "a string", value);
    }

    @Override
    public @NotNull String requireString(@NotNull String key) throws ContentException {
        return getString(key).orElseThrow(() -> missing(key));
    }

    @Override
    public @NotNull Optional<Integer> getInt(@NotNull String key) throws ContentException {
        Object value = values.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return Optional.of(((Number) value).intValue());
        }
        if (value instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            return Optional.of(l.intValue());
        }
        throw typeError(key, "an integer", value);
    }

    @Override
    public int requireInt(@NotNull String key) throws ContentException {
        return getInt(key).orElseThrow(() -> missing(key));
    }

    @Override
    public @NotNull Optional<Double> getDouble(@NotNull String key) throws ContentException {
        Object value = values.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Number number) {
            return Optional.of(number.doubleValue());
        }
        throw typeError(key, "a number", value);
    }

    @Override
    public double requireDouble(@NotNull String key) throws ContentException {
        return getDouble(key).orElseThrow(() -> missing(key));
    }

    @Override
    public boolean getBoolean(@NotNull String key, boolean fallback) throws ContentException {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw typeError(key, "true or false", value);
    }

    @Override
    public @NotNull Optional<ConfigSection> getSection(@NotNull String key) throws ContentException {
        Object value = values.get(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(of(child(key), value));
    }

    @Override
    public boolean isSection(@NotNull String key) {
        return values.get(key) instanceof Map<?, ?>;
    }

    @Override
    public @NotNull ConfigSection requireSection(@NotNull String key) throws ContentException {
        return getSection(key).orElseThrow(() -> missing(key));
    }

    @Override
    public @NotNull List<ConfigSection> getSectionList(@NotNull String key) throws ContentException {
        Object value = values.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw typeError(key, "a list", value);
        }
        List<ConfigSection> sections = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            sections.add(of(child(key) + "[" + i + "]", list.get(i)));
        }
        return sections;
    }

    @Override
    public @NotNull List<String> getStringList(@NotNull String key) throws ContentException {
        Object value = values.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw typeError(key, "a list", value);
        }
        List<String> strings = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof String || element instanceof Number || element instanceof Boolean)) {
                throw typeError(key, "a list of strings", value);
            }
            strings.add(String.valueOf(element));
        }
        return strings;
    }

    @Override
    public @NotNull PrismKey requireKey(@NotNull String key) throws ContentException {
        String text = requireString(key);
        try {
            return PrismKey.parse(text);
        } catch (IllegalArgumentException e) {
            throw new ContentException(child(key), e.getMessage());
        }
    }

    @Override
    public void rejectUnknownKeys(@NotNull Set<String> allowed) throws ContentException {
        for (String key : values.keySet()) {
            if (!allowed.contains(key)) {
                throw new ContentException(child(key), "unknown key (allowed: " + String.join(", ", allowed) + ")");
            }
        }
    }

    @Override
    public @NotNull ContentException error(@NotNull String message) {
        return new ContentException(path, message);
    }

    private String child(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    private ContentException missing(String key) {
        return new ContentException(child(key), "required value is missing");
    }

    private ContentException typeError(String key, String expected, Object actual) {
        return new ContentException(child(key), "expected " + expected + ", got " + describe(actual));
    }

    private static String describe(Object value) {
        if (value == null) {
            return "nothing";
        }
        if (value instanceof Map) {
            return "a mapping";
        }
        if (value instanceof List) {
            return "a list";
        }
        return "'" + value + "' (" + value.getClass().getSimpleName() + ")";
    }
}
