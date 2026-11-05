package com.aitchn.prism.validation.ae2.runtime;

import com.aitchn.prism.api.data.DataStore;
import com.aitchn.prism.validation.ae2.crafting.Pattern;
import com.aitchn.prism.validation.ae2.crafting.PatternCodec;
import com.aitchn.prism.validation.ae2.grid.GridPos;
import com.aitchn.prism.validation.ae2.grid.NodeKind;
import com.aitchn.prism.validation.ae2.storage.StorageCell;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Everything AE2 must remember: which blocks are ME nodes, what is in each drive and pattern provider, what is in
 * each storage cell, and how much energy each network has. Prism offers a string key-value store and nothing
 * per-block, so this class keeps its own position-keyed index and writes changed keys in batches.
 */
public final class Ae2State {

    public static final int DRIVE_SLOTS = 10;

    private final DataStore store;
    private final Logger logger;
    private final Map<GridPos, NodeKind> nodes = new ConcurrentHashMap<>();
    private final Map<GridPos, UUID[]> drives = new ConcurrentHashMap<>();
    private final Map<GridPos, List<Pattern>> providers = new ConcurrentHashMap<>();
    private final Map<UUID, StorageCell> cells = new ConcurrentHashMap<>();
    private final Map<GridPos, Double> energy = new ConcurrentHashMap<>();
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();

    public Ae2State(@NotNull DataStore store, @NotNull Logger logger) {
        this.store = store;
        this.logger = logger;
    }

    // --- loading -----------------------------------------------------------------------------------------------

    /** Reads the node, drive, provider and energy indexes. Cells are loaded when a drive first references them. */
    public void load() {
        for (String key : store.keys("node/")) {
            try {
                nodes.put(GridPos.decode(key.substring(5)), NodeKind.fromId(store.get(key).orElseThrow()));
            } catch (RuntimeException e) {
                logger.warning("skipping unreadable " + key + ": " + e.getMessage());
            }
        }
        for (String key : store.keys("drive/")) {
            try {
                GridPos pos = GridPos.decode(key.substring(6));
                UUID[] slots = new UUID[DRIVE_SLOTS];
                String[] parts = store.get(key).orElse("").split(",", -1);
                for (int i = 0; i < Math.min(parts.length, DRIVE_SLOTS); i++) {
                    if (!parts[i].isEmpty() && !parts[i].equals("-")) {
                        slots[i] = UUID.fromString(parts[i]);
                    }
                }
                drives.put(pos, slots);
            } catch (RuntimeException e) {
                logger.warning("skipping unreadable " + key + ": " + e.getMessage());
            }
        }
        for (String key : store.keys("provider/")) {
            GridPos pos = GridPos.decode(key.substring(9));
            providers.put(pos, new ArrayList<>(PatternCodec.decodeAll(store.get(key).orElse(""))));
        }
        for (String key : store.keys("energy/")) {
            try {
                energy.put(GridPos.decode(key.substring(7)), Double.parseDouble(store.get(key).orElseThrow()));
            } catch (RuntimeException e) {
                logger.warning("skipping unreadable " + key + ": " + e.getMessage());
            }
        }
    }

    // --- nodes ---------------------------------------------------------------------------------------------------

    public @NotNull Map<GridPos, NodeKind> nodes() {
        return Map.copyOf(nodes);
    }

    public void addNode(@NotNull GridPos pos, @NotNull NodeKind kind) {
        nodes.put(pos, kind);
        dirty.add("node/" + pos.encode());
    }

    public @NotNull Optional<NodeKind> removeNode(@NotNull GridPos pos) {
        NodeKind kind = nodes.remove(pos);
        dirty.add("node/" + pos.encode());
        return Optional.ofNullable(kind);
    }

    // --- drives and cells ------------------------------------------------------------------------------------------

    /** The cell in {@code slot} of the drive at {@code pos}, loaded from storage if needed. */
    public @NotNull Optional<StorageCell> driveCell(@NotNull GridPos pos, int slot) {
        UUID[] slots = drives.get(pos);
        return slots == null || slots[slot] == null ? Optional.empty() : cell(slots[slot]);
    }

    public @NotNull List<StorageCell> driveCells(@NotNull GridPos pos) {
        List<StorageCell> result = new ArrayList<>();
        for (int i = 0; i < DRIVE_SLOTS; i++) {
            driveCell(pos, i).ifPresent(result::add);
        }
        return result;
    }

    public void putCellInDrive(@NotNull GridPos pos, int slot, @NotNull StorageCell cell) {
        cells.put(cell.id(), cell);
        drives.computeIfAbsent(pos, p -> new UUID[DRIVE_SLOTS])[slot] = cell.id();
        dirty.add("drive/" + pos.encode());
        markCell(cell);
    }

    public @NotNull Optional<StorageCell> takeCellFromDrive(@NotNull GridPos pos, int slot) {
        Optional<StorageCell> cell = driveCell(pos, slot);
        if (cell.isPresent()) {
            drives.get(pos)[slot] = null;
            dirty.add("drive/" + pos.encode());
        }
        return cell;
    }

    public void removeDrive(@NotNull GridPos pos) {
        drives.remove(pos);
        dirty.add("drive/" + pos.encode());
    }

    public @NotNull Optional<StorageCell> cell(@NotNull UUID id) {
        StorageCell cached = cells.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        return store.get("cell/" + id).map(text -> {
            int newline = text.indexOf('\n');
            StorageCell.Tier tier = StorageCell.Tier.valueOf(newline < 0 ? text : text.substring(0, newline));
            StorageCell loaded = StorageCell.load(id, tier, newline < 0 ? "" : text.substring(newline + 1));
            StorageCell raced = cells.putIfAbsent(id, loaded);
            return raced != null ? raced : loaded;
        });
    }

    public void registerCell(@NotNull StorageCell cell) {
        cells.put(cell.id(), cell);
        markCell(cell);
    }

    /** Call after changing a cell's contents so it is written on the next flush. */
    public void markCell(@NotNull StorageCell cell) {
        dirty.add("cell/" + cell.id());
    }

    // --- pattern providers -----------------------------------------------------------------------------------------

    public @NotNull List<Pattern> patterns(@NotNull GridPos provider) {
        List<Pattern> list = providers.get(provider);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    public void addPattern(@NotNull GridPos provider, @NotNull Pattern pattern) {
        List<Pattern> list = providers.computeIfAbsent(provider, p -> new ArrayList<>());
        synchronized (list) {
            list.add(pattern);
        }
        dirty.add("provider/" + provider.encode());
    }

    public @NotNull Optional<Pattern> removePattern(@NotNull GridPos provider, int index) {
        List<Pattern> list = providers.get(provider);
        if (list == null) {
            return Optional.empty();
        }
        synchronized (list) {
            if (index < 0 || index >= list.size()) {
                return Optional.empty();
            }
            dirty.add("provider/" + provider.encode());
            return Optional.of(list.remove(index));
        }
    }

    public @NotNull List<Pattern> removeProvider(@NotNull GridPos provider) {
        List<Pattern> list = providers.remove(provider);
        dirty.add("provider/" + provider.encode());
        return list == null ? List.of() : List.copyOf(list);
    }

    // --- energy -----------------------------------------------------------------------------------------------------

    public double energy(@NotNull GridPos networkId) {
        return energy.getOrDefault(networkId, 0.0);
    }

    public void setEnergy(@NotNull GridPos networkId, double value) {
        if (value > 0) {
            energy.put(networkId, value);
        } else {
            energy.remove(networkId);
        }
        dirty.add("energy/" + networkId.encode());
    }

    // --- writing ----------------------------------------------------------------------------------------------------

    /** Writes every changed key. Blocking: call from an async thread, never from a tick. */
    public void flush() {
        for (String key : List.copyOf(dirty)) {
            if (!dirty.remove(key)) {
                continue;
            }
            String value = render(key);
            if (value == null) {
                store.remove(key);
            } else {
                store.put(key, value);
            }
        }
    }

    private @Nullable String render(String key) {
        int slash = key.indexOf('/');
        String kind = key.substring(0, slash);
        String id = key.substring(slash + 1);
        return switch (kind) {
            case "node" -> {
                NodeKind node = nodes.get(GridPos.decode(id));
                yield node == null ? null : node.id();
            }
            case "drive" -> {
                UUID[] slots = drives.get(GridPos.decode(id));
                if (slots == null) {
                    yield null;
                }
                StringBuilder out = new StringBuilder();
                for (int i = 0; i < slots.length; i++) {
                    out.append(i == 0 ? "" : ",").append(slots[i] == null ? "-" : slots[i].toString());
                }
                yield out.toString();
            }
            case "provider" -> {
                List<Pattern> list = providers.get(GridPos.decode(id));
                yield list == null ? null : PatternCodec.encodeAll(patterns(GridPos.decode(id)));
            }
            case "cell" -> {
                StorageCell cell = cells.get(UUID.fromString(id));
                yield cell == null ? null : cell.tier().name() + "\n" + cell.serialize();
            }
            case "energy" -> {
                Double stored = energy.get(GridPos.decode(id));
                yield stored == null ? null : stored.toString();
            }
            default -> null;
        };
    }
}
