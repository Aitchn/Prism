package com.aitchn.validation.botania.logic;

import java.util.List;

/**
 * Everything the rules need from the world. The Bukkit adapter implements it for the thread that is running, so
 * {@link #isOwned} lets long-range logic (bursts) hand over when it leaves the current Folia region.
 */
public interface WorldView {

    /** True when the calling thread may read and write this position (always true outside Folia). */
    boolean isOwned(Pos pos);

    /** True for blocks that stop a mana burst. */
    boolean isSolid(Pos pos);

    boolean isDaylight(Pos pos);

    /** {@code minecraft:stone}, {@code botania:livingrock} ... or {@code minecraft:air}. */
    String blockId(Pos pos);

    /** Replaces the block with another (vanilla or custom) block. Returns false when it was not possible. */
    boolean convert(Pos pos, String blockId);

    /** Item ids of dropped items within {@code radius} blocks of {@code pos}. */
    List<String> droppedItems(Pos pos, double radius);

    /** Removes one dropped item with this id near {@code pos}. */
    boolean consumeDroppedItem(Pos pos, double radius, String itemId);

    void dropItem(Pos pos, String itemId, int amount);

    /** The recipes in force. */
    RecipeBook recipes();

    /**
     * The mana collector at {@code pos}. The plugin answers through Prism's block capability lookup, so any mod's
     * block that offers the mana capability works; the default reads the local network (tests).
     */
    default java.util.Optional<ManaCollector> collectorAt(Pos pos, ManaNetwork network) {
        return network.collector(pos);
    }
}
