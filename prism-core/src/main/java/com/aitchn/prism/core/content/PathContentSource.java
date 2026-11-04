package com.aitchn.prism.core.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

/** Content from a directory, or from the root of a jar opened as a {@link java.nio.file.FileSystem}. */
public final class PathContentSource implements ContentSource {

    private final Path root;

    public PathContentSource(@NotNull Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    @Override
    public @NotNull List<String> list(@NotNull String directory) throws IOException {
        Path start = resolve(directory);
        if (!Files.isDirectory(start)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(start)) {
            return walk.filter(Files::isRegularFile)
                    .map(file -> root.relativize(file).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    @Override
    public boolean exists(@NotNull String path) {
        return Files.exists(resolve(path));
    }

    @Override
    public @NotNull String readText(@NotNull String path) throws IOException {
        return Files.readString(resolve(path), StandardCharsets.UTF_8);
    }

    private Path resolve(String path) {
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root.normalize())) {
            throw new IllegalArgumentException("Path escapes the content root: " + path);
        }
        return resolved;
    }
}
