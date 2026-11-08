package com.aitchn.prism.api.block;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.BlockDefinition;
import java.util.List;
import java.util.Optional;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Places, finds and removes custom blocks. Every method that takes a block or location must be called on the thread
 * that owns it (on Folia: the region thread); see {@link com.aitchn.prism.api.scheduler.PrismScheduler}.
 */
public interface BlockService {

    /** Places {@code key} facing north with no placer. */
    default boolean place(@NotNull PrismKey key, @NotNull Location location) {
        return place(key, location, null, BlockFace.NORTH);
    }

    /**
     * Places {@code key} at {@code location}'s block and runs its {@link BlockPlaceBehavior}s. Returns false if the key
     * is unknown or the spot is not free. The facing is ignored for blocks without {@code prism:rotation}.
     */
    default boolean place(@NotNull PrismKey key, @NotNull Location location, @Nullable Player placer,
            @NotNull BlockFace facing) {
        return place(key, location, placer, facing, null);
    }

    /**
     * Like {@link #place(PrismKey, Location, Player, BlockFace)}, from {@code from}: place behaviors see a copy of it in
     * {@link BlockContext#placedFrom()}, for blocks that take back data their dropped item carries.
     */
    boolean place(@NotNull PrismKey key, @NotNull Location location, @Nullable Player placer, @NotNull BlockFace facing,
            @Nullable org.bukkit.inventory.ItemStack from);

    /** The custom block at {@code block}, if there is one. Constant time; safe to call every tick. */
    @NotNull Optional<CustomBlock> get(@NotNull Block block);

    /** The key of the custom block at {@code block}, if there is one. */
    default @NotNull Optional<PrismKey> identify(@NotNull Block block) {
        return get(block).map(CustomBlock::key);
    }

    /** Every custom block in a loaded chunk, in no particular order. */
    @NotNull List<CustomBlock> blocksIn(@NotNull Chunk chunk);

    /**
     * Removes the custom block (carrier, appearance and data) after running its {@link BlockRemoveBehavior}s.
     * Returns false if it was not a custom block.
     */
    boolean remove(@NotNull Block block, boolean dropItem);

    /** The structure {@code controller} stands for, if it is a {@code prism:structure} controller and formed. */
    @NotNull Optional<FormedStructure> structure(@NotNull Block controller);

    /** The controller of the formed structure {@code member} belongs to, if any. */
    @NotNull Optional<Block> controllerOf(@NotNull Block member);

    /**
     * Works out the sides a connected block ({@code prism:connections}) reaches again and redraws it if they changed.
     * Prism already does this when the block or a neighbour is placed or removed; call it when a
     * {@link BlockConnectionBehavior} would now answer differently (a side was switched off in a menu). Does nothing for
     * other blocks.
     */
    void refreshConnections(@NotNull Block block);

    /**
     * Switches the block to another of its {@code prism:variants} looks, for example from {@code idle} to
     * {@code active}. Shown to Java and Bedrock players at once. Returns false if the block is not custom or has no
     * such variant. Cheap when the variant does not change.
     */
    boolean setVariant(@NotNull Block block, @NotNull String variant);

    /** The currently registered definition of {@code key}. */
    @NotNull Optional<BlockDefinition> definition(@NotNull PrismKey key);

    /**
     * Asks {@code block} for {@code capability} on its {@code side}. Custom blocks answer through their
     * {@link BlockCapabilityProvider} behaviors; vanilla blocks answer the capabilities in {@link PrismCapabilities}.
     *
     * @param side the face of {@code block} being asked (for a neighbour lookup: the face pointing back at the caller)
     */
    <T> @NotNull Optional<T> capability(
            @NotNull Block block, @Nullable BlockFace side, @NotNull BlockCapability<T> capability);
}
