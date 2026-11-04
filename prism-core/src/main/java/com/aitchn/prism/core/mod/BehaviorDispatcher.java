package com.aitchn.prism.core.mod;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.item.ItemUseBehavior;
import com.aitchn.prism.api.item.ItemUseContext;
import com.aitchn.prism.api.registry.Registry;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Routes game events to behaviors by capability. It never branches on an item id: it asks the registry for the
 * definition and runs whatever capability instances that definition has. A failing behavior is logged and skipped so
 * one broken mod cannot break the event for everyone else.
 */
public final class BehaviorDispatcher {

    private final Registry<ItemDefinition> items;
    private final Predicate<Player> ownedByCurrentThread;
    private final Logger logger;

    /**
     * @param ownedByCurrentThread thread ownership check; on Folia this must be the region/entity ownership test
     */
    public BehaviorDispatcher(
            @NotNull Registry<ItemDefinition> items,
            @NotNull Predicate<Player> ownedByCurrentThread,
            @NotNull Logger logger) {
        this.items = items;
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
}
