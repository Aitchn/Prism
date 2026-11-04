package com.aitchn.prism.core.content;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;

/** In-memory content, for tests that need no files. */
public final class MemoryContentSource implements ContentSource {

    private final Map<String, String> files = new TreeMap<>();

    public @NotNull MemoryContentSource put(@NotNull String path, @NotNull String text) {
        files.put(path, text);
        return this;
    }

    @Override
    public @NotNull List<String> list(@NotNull String directory) {
        String prefix = directory.endsWith("/") ? directory : directory + "/";
        return files.keySet().stream().filter(path -> path.startsWith(prefix)).toList();
    }

    @Override
    public boolean exists(@NotNull String path) {
        return files.containsKey(path);
    }

    @Override
    public @NotNull String readText(@NotNull String path) throws java.io.IOException {
        String text = files.get(path);
        if (text == null) {
            throw new java.io.FileNotFoundException(path);
        }
        return text;
    }
}
