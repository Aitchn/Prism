package com.aitchn.prism.plugin;

import com.aitchn.prism.api.block.BlockService;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * The few vanilla behaviours that could still act on an inert {@link Carrier}. The carriers have no states that change,
 * no ticks and no neighbour reactions, so the guards earlier versions needed against note block re-tuning, instrument
 * changes and tripwire connecting are gone (neighbour physics events, for example, now flow normally). What is left is
 * defensive: a tool use that would turn a carrier into another block (no vanilla tool does that to a mushroom block
 * today) and fluid flowing into structure void.
 */
final class NativeStateListener implements Listener {

    private final BlockService blocks;

    NativeStateListener(BlockService blocks) {
        this.blocks = blocks;
    }

    // HIGHEST: denying block use marks the event cancelled, so it must run after Prism's own NORMAL dispatch
    // (BlockListener), which skips cancelled events.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null || Carrier.of(clicked.getType()) == null) {
            return;
        }
        // Right click only: denying a left click would also stop the player from breaking the block. Denying the use
        // keeps a tool from turning the carrier into another block.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && blocks.identify(clicked).isPresent()) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    /** Fluids would replace the passable carrier (and with it the block). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block to = event.getToBlock();
        if (to.getType() == Carrier.PASSABLE.material() && blocks.identify(to).isPresent()) {
            event.setCancelled(true);
        }
    }
}
