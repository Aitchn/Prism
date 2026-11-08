package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.BlockBreakBehavior;
import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.content.BlockDefinition;
import com.aitchn.prism.api.content.ItemDefinition;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.registry.Registry;
import com.aitchn.prism.core.block.Facings;
import com.aitchn.prism.core.mod.BehaviorDispatcher;
import java.util.Iterator;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * Translates Bukkit block events into capability dispatches, places custom blocks from their items and keeps custom
 * blocks from being moved or lost. Contains no block-specific logic.
 */
final class BlockListener implements Listener {

    private final ItemService items;
    private final PrismBlockService blocks;
    private final Registry<ItemDefinition> itemDefinitions;
    private final BehaviorDispatcher dispatcher;
    /** The place event Prism fires itself as a protection check; not a real vanilla placement. */
    private Event probing;

    BlockListener(ItemService items, PrismBlockService blocks, Registry<ItemDefinition> itemDefinitions,
            BehaviorDispatcher dispatcher) {
        this.items = items;
        this.blocks = blocks;
        this.itemDefinitions = itemDefinitions;
        this.dispatcher = dispatcher;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Optional<CustomBlock> custom = blocks.get(clicked);
        if (custom.isPresent() && !player.isSneaking()) {
            BlockEventContext ctx = new BlockEventContext(custom.get(), player, event.getBlockFace(),
                    hit(event, clicked), EquipmentSlot.HAND);
            dispatcher.dispatchBlock(custom.get().key(), clicked.getLocation(), BlockInteractBehavior.class,
                    d -> ctx, (b, c) -> ctx.interact(b));
            if (ctx.isCancelled() || ctx.handled()) {
                // A block that used the click owns it: no item use, no vanilla placement next to it. A behavior that
                // called pass() lets the click through.
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
        if (placed.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Block target = clicked.isReplaceable() && blocks.identify(clicked).isEmpty()
                ? clicked : clicked.getRelative(event.getBlockFace());
        if (placeFromItem(player, placed.get(), target, clicked, held) && player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
    }

    /** Where the click landed, relative to the block's minimum corner. */
    private static org.bukkit.util.Vector hit(PlayerInteractEvent event, Block block) {
        var point = event.getInteractionPoint();
        return point == null ? null : point.toVector().subtract(block.getLocation().toVector());
    }

    private boolean placeFromItem(Player player, PrismKey key, Block target, Block against, ItemStack from) {
        Optional<BlockDefinition> definition = blocks.definition(key);
        if (definition.isEmpty() || !(target.isEmpty() || target.isReplaceable())) {
            return false;
        }
        boolean occupied = !target.getWorld().getNearbyEntities(BoundingBox.of(target),
                entity -> entity instanceof LivingEntity && !entity.isDead()).isEmpty();
        if (occupied) {
            return false;
        }
        // Let protection plugins veto the placement the same way they veto vanilla blocks.
        BlockPlaceEvent check = new BlockPlaceEvent(target, target.getState(), against,
                player.getInventory().getItemInMainHand(), player, true, EquipmentSlot.HAND);
        probing = check;
        try {
            check.callEvent();
        } finally {
            probing = null;
        }
        if (check.isCancelled() || !check.canBuild()) {
            return false;
        }
        PrismComponents.Rotation rotation = definition.get().components().get(PrismComponents.ROTATION)
                .orElse(PrismComponents.Rotation.NONE);
        BlockFace facing = BlockFace.valueOf(Facings.facing(rotation, player.getYaw(), player.getPitch()));
        if (!blocks.place(key, target.getLocation(), player, facing, from)) {
            return false;
        }
        player.swingMainHand();
        target.getWorld().playSound(target.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_STONE_PLACE,
                SoundCategory.BLOCKS, 1f, 0.8f);
        return true;
    }

    /** Creative breaks, and survival breaks completed by {@link DigListener}. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<CustomBlock> custom = blocks.get(block);
        if (custom.isEmpty()) {
            if (blocks.identify(block).isPresent()) {
                // A block of a mod that is not loaded: keep it until its mod is back.
                event.setCancelled(true);
            }
            return;
        }
        BlockEventContext ctx = new BlockEventContext(custom.get(), event.getPlayer(), null, null, EquipmentSlot.HAND);
        dispatcher.dispatchBlock(custom.get().key(), block.getLocation(), BlockBreakBehavior.class,
                d -> ctx, (b, c) -> b.onBreak(c));
        if (ctx.isCancelled()) {
            event.setCancelled(true);
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
        boolean drop = event.getPlayer().getGameMode() != GameMode.CREATIVE;
        breakEffects(block, custom.get());
        blocks.remove(block, drop);
    }

    private void breakEffects(Block block, CustomBlock custom) {
        var center = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().playSound(center, Sound.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1f, 0.8f);
        custom.definition().drop().flatMap(drop -> items.create(drop, 1)).ifPresent(stack ->
                block.getWorld().spawnParticle(Particle.ITEM, center, 24, 0.25, 0.25, 0.25, 0.05, stack));
    }

    /** Vanilla blocks placed or broken next to custom blocks are neighbour changes too. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanillaPlace(BlockPlaceEvent event) {
        if (event != probing) {
            blocks.notifyNeighbors(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanillaBreak(BlockBreakEvent event) {
        if (blocks.identify(event.getBlock()).isEmpty()) {
            // The block is still there while the event runs; neighbours look once it is gone.
            blocks.notifyNeighborsLater(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeExploded(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeExploded(event.blockList().iterator());
    }

    /** Custom blocks are not pushed: moving the carrier would strand its appearance and data. */
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
                if (blocks.get(block).isPresent()) {
                    blocks.remove(block, true);
                }
            }
        }
    }
}
