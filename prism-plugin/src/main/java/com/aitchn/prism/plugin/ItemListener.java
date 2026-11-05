package com.aitchn.prism.plugin;

import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.item.ItemUseContext;
import com.aitchn.prism.core.mod.BehaviorDispatcher;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
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

    /**
     * Not {@code ignoreCancelled}: Bukkit reports every right click in the air as cancelled (there is no block to
     * use), which would make items unusable in the air. What matters is whether the item use itself was denied.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.useItemInHand() == Event.Result.DENY) {
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
        public Block clickedBlock() {
            return event.getClickedBlock();
        }

        @Override
        public BlockFace clickedFace() {
            return event.getClickedBlock() == null ? null : event.getBlockFace();
        }

        @Override
        public void cancelVanillaAction() {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }
}
