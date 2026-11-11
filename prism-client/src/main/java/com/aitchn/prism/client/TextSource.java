package com.aitchn.prism.client;

import com.aitchn.prism.core.lang.ItemTextRenderer;
import org.bukkit.entity.Player;

/**
 * What the item text rewriter needs from the server. Called from network threads: implementations answer from
 * immutable snapshots or concurrent maps and never touch world state.
 */
public interface TextSource {

    /** Renders and restores stacks with the current translation tables. */
    ItemTextRenderer renderer();

    /** The normalised locale of {@code player} (for example {@code zh_tw}). */
    String locale(Player player);
}
