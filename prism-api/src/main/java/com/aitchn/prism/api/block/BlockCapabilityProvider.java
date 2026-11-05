package com.aitchn.prism.api.block;

import com.aitchn.prism.api.content.Behavior;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Capability: lets other blocks find services on this block through {@link BlockService#capability}. */
public interface BlockCapabilityProvider extends Behavior {

    /**
     * The service for {@code capability} on {@code side} of {@code block}, or null if this block offers none there.
     *
     * @param side the face being asked, seen from this block; null when the caller does not care about sides
     */
    <T> @Nullable T capability(@NotNull CustomBlock block, @Nullable BlockFace side, @NotNull BlockCapability<T> capability);
}
