package com.aitchn.prism.core.block;

import com.aitchn.prism.api.data.DataStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.jetbrains.annotations.NotNull;

/**
 * Gives every block look ({@link Appearances.Look#id()}) a stable note block state. Assignments are persisted in a
 * {@link DataStore}, so a look keeps its state across restarts and across changes to the set of installed mods; a
 * state is never reused for a different look, even when the mod that owned it is gone (placed blocks would otherwise
 * change their appearance). Ported from the M5b native backend.
 */
public final class StateAllocator {

    private static final String PREFIX = "native:";

    private final DataStore store;
    private final Map<String, Integer> byId = new HashMap<>();
    private final Map<Integer, String> byIndex = new HashMap<>();

    public StateAllocator(@NotNull DataStore store) {
        this.store = store;
        for (String stored : store.keys(PREFIX)) {
            store.get(stored).ifPresent(value -> {
                try {
                    int index = Integer.parseInt(value);
                    String id = stored.substring(PREFIX.length());
                    byId.put(id, index);
                    byIndex.put(index, id);
                } catch (NumberFormatException ignored) {
                    // a corrupt row is ignored
                }
            });
        }
    }

    /** The state index of {@code id}, allocating the lowest free one (from 1) on first use. */
    public synchronized @NotNull OptionalInt allocate(@NotNull String id) {
        Integer existing = byId.get(id);
        if (existing != null) {
            return OptionalInt.of(existing);
        }
        for (int index = 1; index < NoteBlockStates.COUNT; index++) {
            if (!byIndex.containsKey(index)) {
                byId.put(id, index);
                byIndex.put(index, id);
                store.put(PREFIX + id, Integer.toString(index));
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    /** The state of {@code id} if it already has one; never allocates. */
    public synchronized @NotNull OptionalInt indexOf(@NotNull String id) {
        Integer existing = byId.get(id);
        return existing == null ? OptionalInt.empty() : OptionalInt.of(existing);
    }

    /** The look that owns {@code index}, if any (including looks whose mod is currently absent). */
    public synchronized @NotNull Optional<String> idFor(int index) {
        return Optional.ofNullable(byIndex.get(index));
    }

    /** A snapshot of every assignment, look id to index. */
    public synchronized @NotNull Map<String, Integer> assignments() {
        return Map.copyOf(byId);
    }
}
