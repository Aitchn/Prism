package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import java.util.Set;

/** Which definitions currently have pack assets. Replaced as a whole whenever the pack is re-resolved. */
final class Appearance {

    private volatile Set<PrismKey> items = Set.of();
    private volatile Set<PrismKey> blocks = Set.of();

    void update(Set<PrismKey> items, Set<PrismKey> blocks) {
        this.items = items;
        this.blocks = blocks;
    }

    boolean hasItem(PrismKey key) {
        return items.contains(key);
    }

    boolean hasBlock(PrismKey key) {
        return blocks.contains(key);
    }
}
