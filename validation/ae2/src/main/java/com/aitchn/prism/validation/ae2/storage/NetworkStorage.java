package com.aitchn.prism.validation.ae2.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;

/**
 * The combined inventory of every cell in a network. Inserts prefer cells that already hold the item, then higher
 * priority; extracts drain lower priority cells first.
 */
public final class NetworkStorage implements StorageView {

    private final List<StorageCell> cells;

    public NetworkStorage(@NotNull List<StorageCell> cells) {
        this.cells = List.copyOf(cells);
    }

    public @NotNull List<StorageCell> cells() {
        return cells;
    }

    @Override
    public synchronized long insert(@NotNull ItemKey key, long amount) {
        List<StorageCell> order = new ArrayList<>(cells);
        order.sort(Comparator.<StorageCell>comparingInt(cell -> cell.count(key) > 0 ? 0 : 1)
                .thenComparing(Comparator.<StorageCell>comparingInt(StorageCell::priority).reversed())
                .thenComparing(StorageCell::id));
        long left = amount;
        for (StorageCell cell : order) {
            if (left <= 0) {
                break;
            }
            left -= cell.insert(key, left);
        }
        return amount - Math.max(0, left);
    }

    @Override
    public synchronized long extract(@NotNull ItemKey key, long amount) {
        List<StorageCell> order = new ArrayList<>(cells);
        order.sort(Comparator.<StorageCell>comparingInt(StorageCell::priority).thenComparing(StorageCell::id));
        long taken = 0;
        for (StorageCell cell : order) {
            if (taken >= amount) {
                break;
            }
            taken += cell.extract(key, amount - taken);
        }
        return taken;
    }

    @Override
    public synchronized long count(@NotNull ItemKey key) {
        long total = 0;
        for (StorageCell cell : cells) {
            total += cell.count(key);
        }
        return total;
    }

    @Override
    public synchronized @NotNull Map<ItemKey, Long> snapshot() {
        Map<ItemKey, Long> all = new TreeMap<>();
        for (StorageCell cell : cells) {
            cell.contents().forEach((key, count) -> all.merge(key, count, Long::sum));
        }
        return all;
    }
}
