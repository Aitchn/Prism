package com.aitchn.prism.plugin;

import com.aitchn.prism.platform.spi.PlatformScheduler;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * The correction loop. The Java client predicts neighbour shape updates on its own when a player places, breaks or
 * clicks next to a block (a note block recomputes its instrument from the block below, a tripwire connects to its
 * neighbours) and the server never resends a block it did not change, so a custom block would keep the wrong look until
 * the chunk is sent again. After every such action, and after one that was denied, the player is sent the look of the
 * target and of its six neighbours again. The send is repeated one tick later because the client applies server block
 * data received while it still waits for the action's acknowledgement only when the acknowledgement arrives.
 */
final class LookReconciler implements Listener {

    private final PrismBlockService blocks;
    private final PlatformScheduler scheduler;

    LookReconciler(PrismBlockService blocks, PlatformScheduler scheduler) {
        this.blocks = blocks;
        this.scheduler = scheduler;
    }

    // MONITOR without ignoreCancelled: a denied action leaves the client with a prediction to undo as well.

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked != null && event.getAction() != Action.PHYSICAL) {
            resend(event.getPlayer(), clicked, event.useInteractedBlock() == Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        resend(event.getPlayer(), event.getBlockPlaced(), event.isCancelled());
        resend(event.getPlayer(), event.getBlockAgainst(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        resend(event.getPlayer(), event.getBlock(), event.isCancelled());
    }

    /** The player started digging: the client may already have predicted a change next to the block. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(BlockDamageEvent event) {
        resend(event.getPlayer(), event.getBlock(), false);
    }

    /**
     * @param denied the action was refused, so the target itself (not only its custom neighbours) is sent again with
     *               what the server really has there
     */
    private void resend(Player player, Block target, boolean denied) {
        if (denied) {
            player.sendBlockChange(target.getLocation(), target.getBlockData());
        }
        blocks.resendLooks(player, target);
        scheduler.runAtLater(target.getLocation(), () -> {
            if (player.isOnline()) {
                blocks.resendLooks(player, target);
            }
        }, 1);
    }
}
