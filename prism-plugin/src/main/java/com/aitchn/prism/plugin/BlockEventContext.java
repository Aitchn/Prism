package com.aitchn.prism.plugin;

import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.content.BlockDefinition;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The {@link BlockContext} handed to block behaviors for one event. */
final class BlockEventContext implements BlockContext {
    private final Block block;
    private final Player player;
    private BlockDefinition definition;
    private boolean cancelled;

    BlockEventContext(Block block, @Nullable Player player) {
        this.block = block;
        this.player = player;
    }

    BlockEventContext with(BlockDefinition definition) {
        this.definition = definition;
        return this;
    }

    boolean cancelled() {
        return cancelled;
    }

    @Override
    public @NotNull Block block() {
        return block;
    }

    @Override
    public @NotNull BlockDefinition definition() {
        return definition;
    }

    @Override
    public @Nullable Player player() {
        return player;
    }

    @Override
    public void cancel() {
        cancelled = true;
    }
}
