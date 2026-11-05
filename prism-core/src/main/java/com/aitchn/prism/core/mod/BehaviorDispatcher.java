package com.aitchn.prism.core.mod;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockBreakBehavior;
import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.block.BlockPlaceBehavior;
import com.aitchn.prism.api.block.BlockRemoveBehavior;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.item.ItemUseBehavior;
import com.aitchn.prism.api.item.ItemUseContext;
import com.aitchn.prism.api.registry.Registry;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Routes game events to behaviors by capability. It never branches on an item id: it asks the registry for the
 * definition and runs whatever capability instances that definition has. A failing behavior is logged and skipped so
 * one broken mod cannot break the event for everyone else.
 */
public final class BehaviorDispatcher {

    private final Registry<ItemDefinition> items;
    private final Registry<BlockDefinition> blocks;
    private final Predicate<Player> ownedByCurrentThread;
    private final Predicate<Location> locationOwned;
    private final Logger logger;

    /**
     * @param ownedByCurrentThread thread ownership check; on Folia this must be the region/entity ownership test
     */
    public BehaviorDispatcher(
            @NotNull Registry<ItemDefinition> items,
            @NotNull Registry<BlockDefinition> blocks,
            @NotNull Predicate<Player> ownedByCurrentThread,
            @NotNull Predicate<Location> locationOwned,
            @NotNull Logger logger) {
        this.items = items;
        this.blocks = blocks;
        this.locationOwned = locationOwned;
        this.ownedByCurrentThread = ownedByCurrentThread;
        this.logger = logger;
    }

    /** Runs the use behaviors of {@code key}. Returns how many ran; 0 if the item is unknown or has none. */
    public int dispatchItemUse(
            @NotNull PrismKey key, @NotNull Player player, @NotNull Function<ItemDefinition, ItemUseContext> context) {
        Optional<ItemDefinition> found = items.get(key);
        if (found.isEmpty()) {
            return 0;
        }
        ItemDefinition definition = found.get();
        var behaviors = definition.behaviors(ItemUseBehavior.class);
        if (behaviors.isEmpty()) {
            return 0;
        }
        if (!ownedByCurrentThread.test(player)) {
            logger.severe("Refusing to run behaviors of " + key + ": current thread does not own the player");
            return 0;
        }
        ItemUseContext ctx = context.apply(definition);
        int ran = 0;
        for (ItemUseBehavior behavior : behaviors) {
            try {
                behavior.onUse(ctx);
                ran++;
            } catch (RuntimeException e) {
                logger.log(java.util.logging.Level.SEVERE,
                        "Behavior " + behavior.getClass().getName() + " of " + key + " (mod " + definition.owner()
                                + ") failed", e);
            }
        }
        return ran;
    }

    /** Runs the interact behaviors of block {@code key}. Returns how many ran. */
    public int dispatchBlockInteract(
            @NotNull PrismKey key, @NotNull Location at, @NotNull Function<BlockDefinition, BlockContext> context) {
        return dispatchBlock(key, at, BlockInteractBehavior.class, context, (b, c) -> b.onInteract(c));
    }

    /** Runs the break behaviors of block {@code key}. Returns how many ran. */
    public int dispatchBlockBreak(
            @NotNull PrismKey key, @NotNull Location at, @NotNull Function<BlockDefinition, BlockContext> context) {
        return dispatchBlock(key, at, BlockBreakBehavior.class, context, (b, c) -> b.onBreak(c));
    }

    /** Runs the place behaviors of block {@code key}. Returns how many ran. */
    public int dispatchBlockPlace(
            @NotNull PrismKey key, @NotNull Location at, @NotNull Function<BlockDefinition, BlockContext> context) {
        return dispatchBlock(key, at, BlockPlaceBehavior.class, context, (b, c) -> b.onPlace(c));
    }

    /** Runs the remove behaviors of block {@code key}. Returns how many ran. */
    public int dispatchBlockRemove(
            @NotNull PrismKey key, @NotNull Location at, @NotNull Function<BlockDefinition, BlockContext> context) {
        return dispatchBlock(key, at, BlockRemoveBehavior.class, context, (b, c) -> b.onRemove(c));
    }

    private <B> int dispatchBlock(
            PrismKey key,
            Location at,
            Class<B> capability,
            Function<BlockDefinition, BlockContext> context,
            java.util.function.BiConsumer<B, BlockContext> call) {
        Optional<BlockDefinition> found = blocks.get(key);
        if (found.isEmpty()) {
            return 0;
        }
        BlockDefinition definition = found.get();
        var behaviors = definition.behaviors(capability);
        if (behaviors.isEmpty()) {
            return 0;
        }
        if (!locationOwned.test(at)) {
            logger.severe("Refusing to run behaviors of " + key + ": current thread does not own the block's region");
            return 0;
        }
        BlockContext ctx = context.apply(definition);
        int ran = 0;
        for (B behavior : behaviors) {
            try {
                call.accept(behavior, ctx);
                ran++;
            } catch (RuntimeException e) {
                logger.log(java.util.logging.Level.SEVERE,
                        "Behavior " + behavior.getClass().getName() + " of " + key + " (mod " + definition.owner()
                                + ") failed", e);
            }
        }
        return ran;
    }
}
