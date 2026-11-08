package com.aitchn.prism.validation.ae2.runtime;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.validation.ae2.Ae2Content;
import com.aitchn.prism.validation.ae2.storage.StorageCell;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * The cells inside one drive. A cell is an ordinary Prism item whose contents live in its own per-stack data; the
 * drive keeps each cell's stack in its per-block data and a live {@link StorageCell} for the network to use. Changed
 * contents are copied back into the stacks by {@link #write}, which must run on the drive's region thread.
 */
public final class DriveState {

    public static final int SLOTS = 10;

    private final StorageCell[] cells = new StorageCell[SLOTS];
    private final ItemStack[] stacks = new ItemStack[SLOTS];
    private volatile boolean dirty;

    /** Reads the cells stored in a drive's per-block data. */
    public static @NotNull DriveState read(@NotNull PersistentDataContainer data) {
        DriveState drive = new DriveState();
        for (int slot = 0; slot < SLOTS; slot++) {
            byte[] bytes = data.get(Ae2Content.slotKey("cell", slot), PersistentDataType.BYTE_ARRAY);
            if (bytes != null) {
                drive.bind(slot, ItemStack.deserializeBytes(bytes));
            }
        }
        drive.dirty = false;
        return drive;
    }

    /** Puts a cell item into {@code slot}; false if the slot is taken or the stack is not a storage cell. */
    public synchronized boolean bind(int slot, @NotNull ItemStack cellItem) {
        if (slot < 0 || slot >= SLOTS || stacks[slot] != null) {
            return false;
        }
        Optional<StorageCell.Tier> tier = Prism.items().identify(cellItem).flatMap(Ae2Content::cellTier);
        if (tier.isEmpty()) {
            return false;
        }
        String contents = cellItem.getPersistentDataContainer().get(Ae2Content.CONTENTS, PersistentDataType.STRING);
        stacks[slot] = cellItem.asOne();
        cells[slot] = StorageCell.load(UUID.randomUUID(), tier.get(), contents == null ? "" : contents);
        dirty = true;
        return true;
    }

    public synchronized @NotNull Optional<StorageCell> cell(int slot) {
        return Optional.ofNullable(cells[slot]);
    }

    public synchronized @NotNull List<StorageCell> cells() {
        List<StorageCell> list = new ArrayList<>();
        for (StorageCell cell : cells) {
            if (cell != null) {
                list.add(cell);
            }
        }
        return list;
    }

    /** The first empty slot, or -1. */
    public synchronized int firstFree() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (stacks[slot] == null) {
                return slot;
            }
        }
        return -1;
    }

    /** Takes the cell out of {@code slot} as an item carrying its current contents. */
    public synchronized @NotNull Optional<ItemStack> eject(int slot) {
        if (stacks[slot] == null) {
            return Optional.empty();
        }
        ItemStack stack = withContents(stacks[slot], cells[slot]);
        stacks[slot] = null;
        cells[slot] = null;
        dirty = true;
        return Optional.of(stack);
    }

    public void markDirty() {
        dirty = true;
    }

    /** True once after the contents changed. */
    public boolean takeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    /** Copies the live contents into the cell items and stores them in {@code data}. */
    public synchronized void write(@NotNull PersistentDataContainer data) {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (stacks[slot] == null) {
                data.remove(Ae2Content.slotKey("cell", slot));
            } else {
                stacks[slot] = withContents(stacks[slot], cells[slot]);
                data.set(Ae2Content.slotKey("cell", slot), PersistentDataType.BYTE_ARRAY,
                        stacks[slot].serializeAsBytes());
            }
        }
    }

    private static ItemStack withContents(ItemStack stack, StorageCell cell) {
        ItemStack copy = stack.clone();
        String contents = cell.serialize();
        Prism.items().editData(copy, pdc -> {
            if (contents.isEmpty()) {
                pdc.remove(Ae2Content.CONTENTS);
            } else {
                pdc.set(Ae2Content.CONTENTS, PersistentDataType.STRING, contents);
            }
        });
        return copy;
    }
}
