package com.aitchn.prism.plugin;

import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.item.ItemUseContext;
import com.aitchn.prism.core.mod.BehaviorDispatcher;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Translates Bukkit events into capability dispatches. Contains no item-specific logic. */
final class ItemListener implements Listener {

    private final ItemService items;
    private final BehaviorDispatcher dispatcher;

    ItemListener(ItemService items, BehaviorDispatcher dispatcher) {
        this.items = items;
        this.dispatcher = dispatcher;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack stack = event.getItem();
        EquipmentSlot hand = event.getHand();
        if (stack == null || hand == null) {
            return;
        }
        items.identify(stack).ifPresent(key -> {
            Player player = event.getPlayer();
            dispatcher.dispatchItemUse(key, player, definition -> new Context(event, player, stack, definition, hand));
        });
    }

    private record Context(
            PlayerInteractEvent event, Player player, ItemStack stack, ItemDefinition definition, EquipmentSlot hand)
            implements ItemUseContext {

        @Override
        public void cancelVanillaAction() {
            event.setCancelled(true);
        }
    }
}
