package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockService;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.core.mod.BehaviorDispatcher;
import java.util.Iterator;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Translates Bukkit block events into capability dispatches and keeps custom blocks from being moved or lost. */
final class BlockListener implements Listener {

    private final ItemService items;
    private final BlockService blocks;
    private final Registry<ItemDefinition> itemDefinitions;
    private final Registry<BlockDefinition> blockDefinitions;
    private final BehaviorDispatcher dispatcher;

    BlockListener(ItemService items, BlockService blocks, Registry<ItemDefinition> itemDefinitions,
            Registry<BlockDefinition> blockDefinitions, BehaviorDispatcher dispatcher) {
        this.items = items;
        this.blocks = blocks;
        this.itemDefinitions = itemDefinitions;
        this.blockDefinitions = blockDefinitions;
        this.dispatcher = dispatcher;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Optional<PrismKey> custom = blocks.identify(clicked);
        if (custom.isPresent()) {
            Ctx ctx = new Ctx(clicked, player);
            dispatcher.dispatchBlockInteract(custom.get(), clicked.getLocation(), definition -> ctx.with(definition));
            if (ctx.cancelled) {
                event.setCancelled(true);
                return;
            }
        }
        ItemStack held = event.getItem();
        if (held == null) {
            return;
        }
        Optional<PrismKey> placed = items.identify(held)
                .flatMap(itemDefinitions::get)
                .flatMap(definition -> definition.components().get(PrismComponents.PLACES_BLOCK))
                .map(PrismComponents.PlacesBlock::block);
        if (placed.isEmpty() || (custom.isPresent() && !player.isSneaking())) {
            return;
        }
        event.setCancelled(true);
        Block target = clicked.getRelative(event.getBlockFace());
        if (blocks.place(placed.get(), target.getLocation()) && player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<PrismKey> custom = blocks.identify(block);
        if (custom.isEmpty()) {
            return;
        }
        Ctx ctx = new Ctx(block, event.getPlayer());
        dispatcher.dispatchBlockBreak(custom.get(), block.getLocation(), definition -> ctx.with(definition));
        if (ctx.cancelled) {
            event.setCancelled(true);
            return;
        }
        event.setDropItems(false);
        blocks.remove(block, event.getPlayer().getGameMode() != GameMode.CREATIVE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeExploded(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeExploded(event.blockList().iterator());
    }

    /** Custom blocks are not pushed: moving the carrier would strand its display entity. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> blocks.identify(b).isPresent())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> blocks.identify(b).isPresent())) {
            event.setCancelled(true);
        }
    }

    private void removeExploded(Iterator<Block> exploded) {
        while (exploded.hasNext()) {
            Block block = exploded.next();
            if (blocks.identify(block).isPresent()) {
                exploded.remove();
                blocks.remove(block, true);
            }
        }
    }

    private static final class Ctx implements BlockContext {
        private final Block block;
        private final Player player;
        private BlockDefinition definition;
        boolean cancelled;

        Ctx(Block block, @Nullable Player player) {
            this.block = block;
            this.player = player;
        }

        Ctx with(BlockDefinition definition) {
            this.definition = definition;
            return this;
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
}
