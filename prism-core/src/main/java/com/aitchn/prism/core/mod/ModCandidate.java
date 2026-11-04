package com.aitchn.prism.core.mod;

import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.core.content.ContentSource;
import java.util.Objects;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

/** Everything the manager needs to know about one mod, with no Bukkit types. */
public record ModCandidate(
        @NotNull String id,
        @NotNull String namespace,
        @NotNull String contentRoot,
        @NotNull ContentSource content,
        @NotNull PrismMod entry,
        @NotNull Logger logger) {

    public ModCandidate {
        Objects.requireNonNull(id);
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(contentRoot);
        Objects.requireNonNull(content);
        Objects.requireNonNull(entry);
        Objects.requireNonNull(logger);
    }
}
