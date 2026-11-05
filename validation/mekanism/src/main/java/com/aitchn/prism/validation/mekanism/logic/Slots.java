package com.aitchn.prism.validation.mekanism.logic;

/**
 * Keeps a machine's item slots and a menu showing them consistent. The menu reports what a slot holds a tick after the
 * player acted, while the machine may have changed in between (it consumes input, it produces output); these rules
 * apply only what the player changed, so neither side loses or duplicates items in the common cases.
 */
public final class Slots {

    private Slots() {}

    /**
     * New input slot content.
     *
     * @param shown   what the menu showed the last time it drew the slot
     * @param current what the machine holds now (it may have consumed some since)
     * @param now     what the slot holds now, after the player's click
     */
    public static Stack reconcileInput(Stack shown, Stack current, Stack now) {
        if (shown == null && now == null) {
            return current;
        }
        if (now == null) {
            // the player took everything they saw
            if (current == null || shown == null || !current.id().equals(shown.id())) {
                return current;
            }
            int left = current.count() - shown.count();
            return left > 0 ? current.withCount(left) : null;
        }
        if (shown == null || !now.id().equals(shown.id())) {
            if (current != null && current.id().equals(now.id()) && shown == null) {
                return new Stack(now.id(), Math.min(Stack.MAX, current.count() + now.count()));
            }
            return now;
        }
        int delta = now.count() - shown.count();
        int base = current != null && current.id().equals(now.id()) ? current.count() : 0;
        int count = Math.min(Stack.MAX, base + delta);
        return count > 0 ? new Stack(now.id(), count) : null;
    }

    /** New output slot content after the player took some (output slots only lose items to the player). */
    public static Stack reconcileOutput(Stack shown, Stack current, Stack now) {
        if (current == null || shown == null) {
            return current;
        }
        int removed = shown.count() - (now == null ? 0 : now.count());
        if (removed <= 0) {
            return current;
        }
        int left = current.count() - removed;
        return left > 0 ? current.withCount(left) : null;
    }
}
