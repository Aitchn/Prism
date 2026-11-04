package com.aitchn.prism.core.block;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.data.DataStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.jetbrains.annotations.NotNull;

/**
 * Gives every custom block a stable note block state. Assignments are persisted in a {@link DataStore}, so a block
 * keeps its state across restarts and across changes to the set of installed mods; a state is never reused for a
 * different block, even when the mod that owned it is gone (placed blocks would otherwise change identity).
 */
public final class StateAllocator {

    private static final String PREFIX = "native:";

    private final DataStore store;
    private final Map<PrismKey, Integer> byKey = new HashMap<>();
    private final Map<Integer, PrismKey> byIndex = new HashMap<>();

    public StateAllocator(@NotNull DataStore store) {
        this.store = store;
        for (String stored : store.keys(PREFIX)) {
            store.get(stored).ifPresent(value -> {
                try {
                    PrismKey key = PrismKey.parse(stored.substring(PREFIX.length()));
                    int index = Integer.parseInt(value);
                    byKey.put(key, index);
                    byIndex.put(index, key);
                } catch (IllegalArgumentException ignored) {
                    // a corrupt row is ignored; its state stays reserved only if it parsed
                }
            });
        }
    }

    /** The state index of {@code key}, allocating the lowest free one (from 1) on first use. */
    public synchronized @NotNull OptionalInt allocate(@NotNull PrismKey key) {
        Integer existing = byKey.get(key);
        if (existing != null) {
            return OptionalInt.of(existing);
        }
        for (int index = 1; index < NoteBlockStates.COUNT; index++) {
            if (!byIndex.containsKey(index)) {
                byKey.put(key, index);
                byIndex.put(index, key);
                store.put(PREFIX + key, Integer.toString(index));
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    /** The block that owns {@code index}, if any (including blocks whose mod is currently absent). */
    public synchronized @NotNull Optional<PrismKey> keyFor(int index) {
        return Optional.ofNullable(byIndex.get(index));
    }

    /** A snapshot of every assignment, key to index. */
    public synchronized @NotNull Map<PrismKey, Integer> assignments() {
        return Map.copyOf(byKey);
    }
}
