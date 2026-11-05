package com.aitchn.prism.validation.ae2.storage;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** What terminals and crafting need from a network's stored items. Implementations are thread-safe. */
public interface StorageView {

    /** Stores up to {@code amount}; returns how many were accepted. */
    long insert(@NotNull ItemKey key, long amount);

    /** Takes up to {@code amount}; returns how many were removed. */
    long extract(@NotNull ItemKey key, long amount);

    long count(@NotNull ItemKey key);

    /** Every stored item and its total count. */
    @NotNull Map<ItemKey, Long> snapshot();
}
