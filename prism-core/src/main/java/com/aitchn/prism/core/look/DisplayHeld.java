package com.aitchn.prism.core.look;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/**
 * The display entities one viewer has in one chunk, in primitive arrays: an open-addressing table from a chunk-local
 * block key to {@code width} entity slots, each with the entity id, the cell it was spawned in and a hash of what it
 * draws. 16 to 32 bytes per entity in {@link DisplayLighting#BLOCK} mode (the table is kept at most half full), and no
 * object per block. Not thread-safe; the display layer guards it with its viewer's lock.
 *
 * <p>Entity id {@code 0} means an empty slot (packet entity ids handed out by the server are positive).
 */
public final class DisplayHeld {

    private static final int EMPTY = -1;

    private final int width;
    private int[] keys;
    private int[] ids;
    private int[] cells;
    private int[] hashes;
    private int used;
    private int entities;
    private int allowance = DisplayBudget.NONE;
    private int need = -1;

    /** @param width entity slots per block: 1 for {@link DisplayLighting#BLOCK}, 7 for {@link DisplayLighting#FACES} */
    public DisplayHeld(int width) {
        if (width < 1) {
            throw new IllegalArgumentException("width " + width);
        }
        this.width = width;
        allocate(16);
    }

    /** Slots per block for a lighting mode: one whole-model entity, or six faces and the whole-model fallback. */
    public static int width(@NotNull DisplayLighting lighting) {
        return lighting == DisplayLighting.BLOCK ? 1 : 7;
    }

    /** The slot of part {@code slot} ({@link Dir} index or {@link DisplayLook#WHOLE}) in a table of {@code width}. */
    public static int slotOf(int partSlot, int width) {
        return width == 1 ? 0 : partSlot;
    }

    // ---------------------------------------------------------------- keys

    /** The key of chunk-local {@code (x, z)} at block {@code y} (any world height from -2048 to 2047). */
    public static int key(int localX, int y, int localZ) {
        return ((y + 2048) << 8) | ((localX & 15) << 4) | (localZ & 15);
    }

    public static int keyX(int key) {
        return (key >> 4) & 15;
    }

    public static int keyY(int key) {
        return (key >> 8) - 2048;
    }

    public static int keyZ(int key) {
        return key & 15;
    }

    /** {@code (dx, dy, dz)}, each -1 to 1, as one number. */
    public static int cell(int dx, int dy, int dz) {
        return (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
    }

    // ---------------------------------------------------------------- budget bookkeeping

    /** How many entities the budget lets this chunk show, or {@link DisplayBudget#NONE}. */
    public int allowance() {
        return allowance;
    }

    public void allowance(int allowance) {
        this.allowance = allowance;
    }

    /** How many entities the chunk needed at its last full evaluation, uncapped; -1 if never evaluated. */
    public int need() {
        return need;
    }

    public void need(int need) {
        this.need = need;
    }

    // ---------------------------------------------------------------- slots

    /** The entity in {@code slot} of block {@code key}, or 0. */
    public int id(int key, int slot) {
        int at = find(key);
        return at < 0 ? 0 : ids[at * width + slot];
    }

    public int cellOf(int key, int slot) {
        int at = find(key);
        return at < 0 ? 0 : cells[at * width + slot];
    }

    public int hashOf(int key, int slot) {
        int at = find(key);
        return at < 0 ? 0 : hashes[at * width + slot];
    }

    /** The number of entities block {@code key} has. */
    public int count(int key) {
        int at = find(key);
        if (at < 0) {
            return 0;
        }
        int count = 0;
        for (int s = 0; s < width; s++) {
            count += ids[at * width + s] != 0 ? 1 : 0;
        }
        return count;
    }

    /** Records entity {@code id} (not 0) in {@code slot} of block {@code key}, replacing what was there. */
    public void set(int key, int slot, int id, int cell, int hash) {
        if (id == 0) {
            throw new IllegalArgumentException("entity id 0 marks an empty slot");
        }
        int at = find(key);
        if (at < 0) {
            if ((used + 1) * 2 > keys.length) {
                allocate(keys.length * 2);
            }
            at = insert(key);
        }
        int i = at * width + slot;
        if (ids[i] == 0) {
            entities++;
        }
        ids[i] = id;
        cells[i] = cell;
        hashes[i] = hash;
    }

    /** Empties {@code slot} of block {@code key}; returns the entity that was there, or 0. */
    public int clear(int key, int slot) {
        int at = find(key);
        if (at < 0) {
            return 0;
        }
        int i = at * width + slot;
        int old = ids[i];
        if (old == 0) {
            return 0;
        }
        ids[i] = 0;
        entities--;
        boolean any = false;
        for (int s = 0; s < width; s++) {
            any |= ids[at * width + s] != 0;
        }
        if (!any) {
            delete(at);
        }
        return old;
    }

    /** Empties every slot of block {@code key}; returns the entities that were there. */
    public int @NotNull [] clearAll(int key) {
        int at = find(key);
        if (at < 0) {
            return new int[0];
        }
        int[] removed = new int[count(key)];
        int n = 0;
        for (int s = 0; s < width; s++) {
            int i = at * width + s;
            if (ids[i] != 0) {
                removed[n++] = ids[i];
                ids[i] = 0;
                entities--;
            }
        }
        delete(at);
        return removed;
    }

    /** Total entities held. */
    public int entities() {
        return entities;
    }

    /** Blocks that hold at least one entity. */
    public int blocks() {
        return used;
    }

    /** The keys of every block holding an entity, in no particular order. */
    public int @NotNull [] keys() {
        int[] out = new int[used];
        int n = 0;
        for (int key : keys) {
            if (key != EMPTY) {
                out[n++] = key;
            }
        }
        return out;
    }

    /** Every entity id held. */
    public int @NotNull [] allIds() {
        int[] out = new int[entities];
        int n = 0;
        for (int id : ids) {
            if (id != 0) {
                out[n++] = id;
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- open addressing (linear probing)

    private void allocate(int capacity) {
        int[] oldKeys = keys;
        int[] oldIds = ids;
        int[] oldCells = cells;
        int[] oldHashes = hashes;
        keys = new int[capacity];
        Arrays.fill(keys, EMPTY);
        ids = new int[capacity * width];
        cells = new int[capacity * width];
        hashes = new int[capacity * width];
        used = 0;
        if (oldKeys == null) {
            return;
        }
        for (int at = 0; at < oldKeys.length; at++) {
            if (oldKeys[at] != EMPTY) {
                int to = insert(oldKeys[at]);
                System.arraycopy(oldIds, at * width, ids, to * width, width);
                System.arraycopy(oldCells, at * width, cells, to * width, width);
                System.arraycopy(oldHashes, at * width, hashes, to * width, width);
            }
        }
    }

    private int home(int key) {
        return (key * 0x9E3779B9 >>> 7) & (keys.length - 1);
    }

    private int find(int key) {
        int mask = keys.length - 1;
        for (int at = home(key);; at = (at + 1) & mask) {
            int k = keys[at];
            if (k == key) {
                return at;
            }
            if (k == EMPTY) {
                return -1;
            }
        }
    }

    /** Puts a key that is not present into a free position and returns it; its slots are empty. */
    private int insert(int key) {
        int mask = keys.length - 1;
        int at = home(key);
        while (keys[at] != EMPTY) {
            at = (at + 1) & mask;
        }
        keys[at] = key;
        used++;
        return at;
    }

    /** Removes the key at {@code at} (its slots already empty) and closes the gap (backward shift deletion). */
    private void delete(int at) {
        int mask = keys.length - 1;
        int gap = at;
        keys[gap] = EMPTY;
        used--;
        for (int next = (gap + 1) & mask; keys[next] != EMPTY; next = (next + 1) & mask) {
            int home = home(keys[next]);
            // Move the entry back into the gap unless its home lies cyclically in (gap, next].
            boolean stays = gap <= next ? (gap < home && home <= next) : (gap < home || home <= next);
            if (stays) {
                continue;
            }
            keys[gap] = keys[next];
            System.arraycopy(ids, next * width, ids, gap * width, width);
            System.arraycopy(cells, next * width, cells, gap * width, width);
            System.arraycopy(hashes, next * width, hashes, gap * width, width);
            keys[next] = EMPTY;
            Arrays.fill(ids, next * width, next * width + width, 0);
            gap = next;
        }
    }
}
