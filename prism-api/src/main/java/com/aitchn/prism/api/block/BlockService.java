package com.aitchn.prism.api.block;

import com.aitchn.prism.api.PrismKey;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * Places, recognises and removes custom blocks. Must be called on the thread that owns {@code location}
 * (on Folia: the region thread).
 */
public interface BlockService {

    /** Places {@code key} at {@code location}'s block. Returns false if it is unknown or the spot is not free. */
    boolean place(@NotNull PrismKey key, @NotNull Location location);

    /** The custom block at {@code block}, if it is one. */
    @NotNull Optional<PrismKey> identify(@NotNull Block block);

    /** Removes the custom block (carrier and appearance). Returns false if it was not a custom block. */
    boolean remove(@NotNull Block block, boolean dropItem);
}
