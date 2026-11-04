package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.BlockDefinition;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A block event as seen by a behavior. Valid only during the call. */
public interface BlockContext {

    @NotNull Block block();

    @NotNull BlockDefinition definition();

    /** The player involved, or null for non-player causes. */
    @Nullable Player player();

    /** Stops the default outcome (opening nothing, breaking nothing, ...). */
    void cancel();
}
