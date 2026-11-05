package com.aitchn.prism.validation.ae2.storage;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * One ME storage cell. Capacity follows AE2's byte model: every stored type costs {@code bytes / 128} bytes and every
 * eight items cost one byte, with at most 63 types.
 */
public final class StorageCell {

    public static final int MAX_TYPES = 63;

    /** Cell sizes. */
    public enum Tier {
        K1(1024), K4(4096), K16(16384), K64(65536), K256(262144);

        private final int bytes;

        Tier(int bytes) {
            this.bytes = bytes;
        }

        public int bytes() {
            return bytes;
        }

        public int bytesPerType() {
            return bytes / 128;
        }
    }

    private final UUID id;
    private final Tier tier;
    private final Map<ItemKey, Long> contents = new TreeMap<>();
    private long total;
    private int priority;

    public StorageCell(@NotNull UUID id, @NotNull Tier tier) {
        this.id = id;
        this.tier = tier;
    }

    public @NotNull UUID id() {
        return id;
    }

    public @NotNull Tier tier() {
        return tier;
    }

    public synchronized int priority() {
        return priority;
    }

    public synchronized void priority(int value) {
        priority = value;
    }

    public synchronized long count(@NotNull ItemKey key) {
        return contents.getOrDefault(key, 0L);
    }

    public synchronized int types() {
        return contents.size();
    }

    public synchronized long usedBytes() {
        return (long) contents.size() * tier.bytesPerType() + (total + 7) / 8;
    }

    public synchronized long totalItems() {
        return total;
    }

    public synchronized @NotNull Map<ItemKey, Long> contents() {
        return new TreeMap<>(contents);
    }

    /** Stores up to {@code amount}; returns how many fit. */
    public synchronized long insert(@NotNull ItemKey key, long amount) {
        if (amount <= 0) {
            return 0;
        }
        boolean isNew = !contents.containsKey(key);
        if (isNew && contents.size() >= MAX_TYPES) {
            return 0;
        }
        long typeBytes = (long) (contents.size() + (isNew ? 1 : 0)) * tier.bytesPerType();
        long room = (tier.bytes() - typeBytes) * 8 - total;
        long accepted = Math.min(amount, Math.max(0, room));
        if (accepted > 0) {
            contents.merge(key, accepted, Long::sum);
            total += accepted;
        }
        return accepted;
    }

    /** Removes up to {@code amount}; returns how many were there. */
    public synchronized long extract(@NotNull ItemKey key, long amount) {
        long have = contents.getOrDefault(key, 0L);
        long taken = Math.min(have, Math.max(0, amount));
        if (taken == have && taken > 0) {
            contents.remove(key);
        } else if (taken > 0) {
            contents.put(key, have - taken);
        }
        total -= taken;
        return taken;
    }

    /** One {@code id<TAB>count} line per type, sorted. Round-trips through {@link #load}. */
    public synchronized @NotNull String serialize() {
        StringBuilder out = new StringBuilder();
        contents.forEach((key, count) -> out.append(key.id()).append('\t').append(count).append('\n'));
        return out.toString();
    }

    /** Restores contents written by {@link #serialize}; malformed lines are skipped, and the cell never overfills. */
    public static @NotNull StorageCell load(@NotNull UUID id, @NotNull Tier tier, @NotNull String data) {
        StorageCell cell = new StorageCell(id, tier);
        for (String line : data.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) {
                continue;
            }
            try {
                cell.insert(new ItemKey(line.substring(0, tab)), Long.parseLong(line.substring(tab + 1)));
            } catch (IllegalArgumentException ignored) {
                // a corrupt line loses only its own item
            }
        }
        return cell;
    }
}
