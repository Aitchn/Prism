package com.aitchn.prism.api.data;

import org.jetbrains.annotations.NotNull;

/** Entry point for persistent storage. Obtain it with {@code Prism.data()}. */
public interface DataService {

    /**
     * The store of {@code namespace}, normally your mod's namespace. Stores of different namespaces never see each
     * other's keys. The namespace must match {@code [a-z0-9_.-]+}.
     */
    @NotNull DataStore store(@NotNull String namespace);
}
