package com.aitchn.prism.core.content;

import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Read-only access to a mod's files, addressed by '/'-separated paths relative to the source root. */
public interface ContentSource {

    /** Every file below {@code directory}, recursively, as full relative paths in sorted order. Empty if absent. */
    @NotNull List<String> list(@NotNull String directory) throws IOException;

    boolean exists(@NotNull String path) throws IOException;

    @NotNull String readText(@NotNull String path) throws IOException;
}
