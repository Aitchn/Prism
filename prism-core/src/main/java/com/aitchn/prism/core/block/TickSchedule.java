package com.aitchn.prism.core.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The ticking blocks of one chunk. Not thread safe: owned by the thread that owns the chunk. {@link #tick} advances
 * one server tick and runs every entry whose interval divides the tick count since it was added.
 */
public final class TickSchedule<T> {

    private record Entry<T>(T value, int interval, long start) {}

    private final Map<Long, Entry<T>> entries = new LinkedHashMap<>();
    private long now;

    public void add(long position, T value, int interval) {
        entries.put(position, new Entry<>(value, Math.max(1, interval), now));
    }

    public void remove(long position) {
        entries.remove(position);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** Advances one tick and hands every due value to {@code run}. Entries added by {@code run} wait for the next tick. */
    public void tick(Consumer<T> run) {
        now++;
        List<T> due = new ArrayList<>();
        for (Entry<T> entry : entries.values()) {
            if ((now - entry.start()) % entry.interval() == 0) {
                due.add(entry.value());
            }
        }
        due.forEach(run);
    }
}
